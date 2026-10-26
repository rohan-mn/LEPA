import io
import json
import logging
import os
import re
from datetime import datetime
from pathlib import Path
from typing import Any

import httpx
import pytesseract
from fastapi import FastAPI, File, Form, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from pdf2image import convert_from_bytes
from PIL import Image
from pypdf import PdfReader
from pydantic import BaseModel
from dotenv import load_dotenv

PROJECT_ROOT = Path(__file__).resolve().parent.parent
load_dotenv(PROJECT_ROOT / ".env")

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("lepa-ai")

app = FastAPI(title="LEPA AI Service", version="0.5.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:5173", "http://127.0.0.1:5173"],
    allow_methods=["*"], allow_headers=["*"], allow_credentials=True,
)

OPENROUTER_API_KEY = os.getenv("OPENROUTER_API_KEY", "").strip()
OPENROUTER_MODEL = os.getenv("OPENROUTER_MODEL", "openrouter/free")
OPENROUTER_SITE_URL = os.getenv("OPENROUTER_SITE_URL", "http://localhost:5173")
OPENROUTER_APP_NAME = os.getenv("OPENROUTER_APP_NAME", "LEPA")
OLLAMA_BASE_URL = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434").rstrip("/")
OLLAMA_MODEL = os.getenv("OLLAMA_MODEL", "qwen3:4b")
AI_PROVIDER_PRIORITY = os.getenv("AI_PROVIDER_PRIORITY", "openrouter-first").strip().lower()
if AI_PROVIDER_PRIORITY not in {"openrouter-first", "ollama-first"}:
    log.warning("[LEPA-AI] Unknown AI_PROVIDER_PRIORITY=%s; using openrouter-first", AI_PROVIDER_PRIORITY)
    AI_PROVIDER_PRIORITY = "openrouter-first"


class ChatRequest(BaseModel):
    message: str
    journeyId: int | None = None
    journey: dict[str, Any] | None = None


SYSTEM_PROMPT = """You are LEPA, a cautious personal financial journey-planning assistant inside a bank.
Your job is to help a customer navigate a life event by explaining priorities, deadlines, trade-offs and next actions using the supplied document facts and bank context.
Never claim that a payment was sent unless the supplied system state says it was completed.
Distinguish source-document facts from AI considerations. Never invent exact amounts, dates, balances or payment status.
Treat the deterministic LEPA action plan, cash-flow calculations, balances, active loans and protected reserve as financial truth. Do not contradict or recompute them.
You may explain why the plan recommends reserving or arranging funds, and you may suggest non-binding considerations. Payment authorization always remains with the customer."""


def provider_order() -> list[str]:
    return ["ollama", "openrouter"] if AI_PROVIDER_PRIORITY == "ollama-first" else ["openrouter", "ollama"]


def extract_text(filename: str, data: bytes) -> str:
    ext = Path(filename or "").suffix.lower()
    log.info("[LEPA-AI] Document received: %s (%d bytes)", filename, len(data))
    if ext in {".txt", ".md", ".csv"}:
        return data.decode("utf-8", errors="ignore")
    if ext == ".pdf":
        text = ""
        try:
            reader = PdfReader(io.BytesIO(data))
            text = "\n".join((p.extract_text() or "") for p in reader.pages)
        except Exception as exc:
            log.warning("[LEPA-AI] Native PDF extraction failed: %s", type(exc).__name__)
        if len(text.strip()) >= 80:
            log.info("[LEPA-AI] Extracted %d characters directly from PDF", len(text))
            return text
        try:
            log.info("[LEPA-AI] Falling back to OCR for PDF")
            pages = convert_from_bytes(data, dpi=180, first_page=1, last_page=8)
            text = "\n".join(pytesseract.image_to_string(p) for p in pages)
        except Exception as exc:
            log.warning("[LEPA-AI] OCR unavailable/failed: %s", type(exc).__name__)
        return text
    if ext in {".png", ".jpg", ".jpeg", ".webp", ".tiff", ".bmp"}:
        try:
            return pytesseract.image_to_string(Image.open(io.BytesIO(data)))
        except Exception as exc:
            log.warning("[LEPA-AI] Image OCR failed: %s", type(exc).__name__)
            return ""
    return data.decode("utf-8", errors="ignore")


def clean_json(text: str) -> dict[str, Any]:
    text = text.strip()
    if text.startswith("```"):
        text = re.sub(r"^```(?:json)?\s*", "", text)
        text = re.sub(r"\s*```$", "", text)
    start, end = text.find("{"), text.rfind("}")
    if start >= 0 and end > start:
        text = text[start:end+1]
    return json.loads(text)


async def openrouter_chat(messages: list[dict[str, str]], json_mode: bool = False) -> str:
    if not OPENROUTER_API_KEY:
        raise RuntimeError("OpenRouter key not configured")
    payload: dict[str, Any] = {"model": OPENROUTER_MODEL, "messages": messages, "temperature": 0.1}
    if json_mode and OPENROUTER_MODEL != "openrouter/free":
        payload["response_format"] = {"type": "json_object"}
    headers = {
        "Authorization": f"Bearer {OPENROUTER_API_KEY}",
        "Content-Type": "application/json",
        "HTTP-Referer": OPENROUTER_SITE_URL,
        "X-Title": OPENROUTER_APP_NAME,
    }
    async with httpx.AsyncClient(timeout=75) as client:
        r = await client.post("https://openrouter.ai/api/v1/chat/completions", headers=headers, json=payload)
        r.raise_for_status()
        return r.json()["choices"][0]["message"]["content"]


async def ollama_chat(messages: list[dict[str, str]], json_mode: bool = False) -> str:
    payload: dict[str, Any] = {"model": OLLAMA_MODEL, "messages": messages, "stream": False, "options": {"temperature": 0.1}}
    if json_mode:
        payload["format"] = "json"
    async with httpx.AsyncClient(timeout=90) as client:
        r = await client.post(f"{OLLAMA_BASE_URL}/api/chat", json=payload)
        r.raise_for_status()
        return r.json()["message"]["content"]


async def llm(messages: list[dict[str, str]], json_mode: bool = False) -> tuple[str, str]:
    errors: list[str] = []
    for provider in provider_order():
        try:
            if provider == "openrouter":
                log.info("[LEPA-AI] Trying OpenRouter (%s)", OPENROUTER_MODEL)
                content = await openrouter_chat(messages, json_mode)
                log.info("[LEPA-AI] Provider selected: OpenRouter / %s", OPENROUTER_MODEL)
                return content, f"openrouter:{OPENROUTER_MODEL}"
            log.info("[LEPA-AI] Trying Ollama (%s)", OLLAMA_MODEL)
            content = await ollama_chat(messages, json_mode)
            log.info("[LEPA-AI] Provider selected: Ollama / %s", OLLAMA_MODEL)
            return content, f"ollama:{OLLAMA_MODEL}"
        except Exception as exc:
            errors.append(f"{provider}: {type(exc).__name__}")
            log.warning("[LEPA-AI] %s unavailable: %s", provider, type(exc).__name__)
    raise RuntimeError("; ".join(errors) or "No LLM provider available")


def _date_from_line(line: str) -> str | None:
    month_names = "January|February|March|April|May|June|July|August|September|October|November|December"
    dm = re.search(rf"(\d{{1,2}})\s+({month_names})\s+(\d{{4}})", line, re.I)
    if not dm:
        return None
    try:
        return datetime.strptime(dm.group(0), "%d %B %Y").date().isoformat()
    except Exception:
        return None


def _amount_after_label(text: str, labels: list[str]) -> tuple[float | None, str]:
    for line in text.splitlines():
        low = line.lower()
        if any(label in low for label in labels):
            m = re.search(r"(?:₹|inr|rs\.?|amount\s*[:=-]?)?\s*([0-9][0-9,]{2,})", line, re.I)
            if m:
                return float(m.group(1).replace(",", "")), line
    return None, ""


def deterministic_analysis(text: str, event_type: str) -> dict[str, Any]:
    lower = text.lower()
    if event_type and event_type != "AUTO":
        event = event_type
    elif any(k in lower for k in ["loan sanction", "sanctioned loan", "home loan", "housing loan"]):
        event = "HOME_LOAN"
    elif any(k in lower for k in ["itinerary", "resort", "holiday", "vacation", "business class", "travel package"]):
        event = "TRAVEL"
    elif any(k in lower for k in ["property", "builder", "registration", "down payment"]):
        event = "HOME_PURCHASE"
    elif any(k in lower for k in ["admission", "tuition", "hostel", "university", "college"]):
        event = "COLLEGE_ADMISSION"
    elif any(k in lower for k in ["hospital", "medical", "surgery", "treatment estimate"]):
        event = "MEDICAL"
    elif any(k in lower for k in ["wedding", "marriage", "venue booking", "catering"]):
        event = "MARRIAGE"
    else:
        event = "CUSTOM"

    obligations: list[dict[str, Any]] = []
    specs: dict[str, list[tuple[str, list[str], str]]] = {
        "HOME_LOAN": [
            ("Processing fee", ["processing fee"], "BANK_TRANSFER"),
            ("Margin contribution", ["margin contribution", "borrower contribution", "own contribution"], "BANK_TRANSFER"),
            ("Stamp duty & registration", ["stamp duty", "registration charges"], "BANK_TRANSFER"),
            ("First EMI", ["first emi", "emi amount"], "ACH"),
        ],
        "TRAVEL": [
            ("Flight balance", ["flight balance", "business class flights", "airfare balance"], "CARD"),
            ("Hotel / resort balance", ["resort balance", "hotel balance", "accommodation balance"], "CARD"),
            ("Private transfer", ["private transfer", "airport transfer"], "CARD"),
            ("Experiences", ["experiences", "excursions", "activities package"], "CARD"),
            ("Travel insurance & visa", ["travel insurance", "visa and insurance", "visa & insurance"], "CARD"),
        ],
        "HOME_PURCHASE": [
            ("Booking amount", ["booking amount"], "BANK_TRANSFER"),
            ("Down payment", ["down payment"], "BANK_TRANSFER"),
            ("Builder installment", ["builder installment"], "BANK_TRANSFER"),
            ("Registration reserve", ["registration reserve", "stamp duty"], "BANK_TRANSFER"),
        ],
    }
    for name, labels, rail in specs.get(event, []):
        amount, line = _amount_after_label(text, labels)
        if amount is not None:
            paid = bool(re.search(r"\b(paid|completed|settled)\b", line, re.I))
            obligations.append({"name": name, "amount": amount, "dueDate": _date_from_line(line), "source": "Document", "paymentRail": rail, "alreadyPaid": paid})

    # Generic safe fallback: when the document is not one of the demo templates,
    # extract only lines that look like explicit payment obligations.
    if not obligations:
        obligation_words = ("due", "fee", "payment", "deposit", "installment", "instalment", "premium",
                            "balance", "contribution", "charges", "insurance", "tuition", "hostel",
                            "registration", "emi", "transfer", "invoice", "booking")
        exclusions = ("sanctioned loan amount", "loan amount", "property value", "property price",
                      "total package value", "credit limit", "outstanding balance")
        for raw in text.splitlines():
            line = raw.strip()
            low = line.lower()
            if not line or any(x in low for x in exclusions) or not any(k in low for k in obligation_words):
                continue
            am = re.search(r"(?:₹|inr|rs\.?)\s*([0-9][0-9,]{2,})", line, re.I)
            if not am:
                continue
            amount = float(am.group(1).replace(",", ""))
            name = re.split(r"[:|—-]", line, maxsplit=1)[0].strip()[:80] or "Payment obligation"
            paid = bool(re.search(r"\b(paid|completed|settled)\b", line, re.I))
            obligations.append({"name": name, "amount": amount, "dueDate": _date_from_line(line),
                                "source": "Document", "paymentRail": "BANK_TRANSFER", "alreadyPaid": paid})

    recs = {
        "HOME_LOAN": ["Home insurance review", "Emergency reserve after disbursement", "EMI auto-debit readiness"],
        "TRAVEL": ["Travel contingency reserve", "FX / card limit check", "International payment notifications"],
        "HOME_PURCHASE": ["Home insurance", "Utility activation", "Moving expenses", "Emergency reserve"],
        "COLLEGE_ADMISSION": ["Monthly allowance setup", "Emergency education reserve", "Travel / relocation readiness"],
        "MEDICAL": ["Insurance / claim readiness", "Emergency liquidity reserve", "Follow-up expense check"],
        "MARRIAGE": ["Vendor deadline review", "Contingency reserve", "Large-payment limit check"],
    }.get(event, ["Emergency reserve", "Payment deadline review"])
    recommendations = [{"name": n, "amount": None, "dueDate": None, "source": "AI recommendation"} for n in recs]
    titles = {"HOME_LOAN": "Home Loan Funding Journey", "TRAVEL": "Luxury Holiday Payment Journey", "HOME_PURCHASE": "Home Purchase Journey"}
    log.info("[LEPA-AI] Deterministic fallback extracted %d obligations", len(obligations))
    return {
        "eventType": event,
        "title": titles.get(event, "Life Event Journey"),
        "summary": "Structured using the deterministic fallback because no LLM provider was reachable.",
        "obligations": obligations,
        "recommendations": recommendations,
        "confidence": 0.74,
    }


@app.get("/health")
async def health():
    return {
        "status": "UP",
        "openrouterConfigured": bool(OPENROUTER_API_KEY),
        "openrouterModel": OPENROUTER_MODEL,
        "ollamaModel": OLLAMA_MODEL,
        "priority": AI_PROVIDER_PRIORITY,
        "providerOrder": provider_order() + ["deterministic"],
        "fallback": "deterministic-parser",
    }


@app.post("/analyze")
async def analyze(file: UploadFile = File(...), event_type: str = Form("AUTO")):
    data = await file.read()
    text = extract_text(file.filename or "upload", data)
    clipped = text[:18000]
    log.info("[LEPA-AI] Analysis started; extractedCharacters=%d priority=%s", len(text), AI_PROVIDER_PRIORITY)
    prompt = f"""Analyze this life-event financial document for LEPA.
Requested event type: {event_type}.
Return ONLY valid JSON with this exact top-level structure:
{{
  "eventType": "HOME_LOAN|HOME_PURCHASE|COLLEGE_ADMISSION|NEW_BABY|MARRIAGE|MEDICAL|RETIREMENT|TRAVEL|CUSTOM",
  "title": "short journey title",
  "summary": "one sentence",
  "obligations": [{{"name":"...","amount":12345.0|null,"dueDate":"YYYY-MM-DD"|null,"source":"Document","paymentRail":"BANK_TRANSFER|ACH|RTP|CARD|INTERNAL_TRANSFER","alreadyPaid":false}}],
  "recommendations": [{{"name":"...","amount":null,"dueDate":null,"source":"AI recommendation"}}],
  "confidence": 0.0
}}
Rules:
- obligations must be explicitly supported by the document; do not invent amounts or dates.
- set alreadyPaid=true only when the document explicitly says paid/completed/settled.
- recommendations must stay separate from obligations and must never be treated as required payments.
- when appropriate, return 2-4 practical non-binding considerations (for example readiness checks, contingency planning or missing-information checks) without inventing monetary values or dates.
- this endpoint extracts the event; a deterministic downstream planner will combine the extracted facts with bank balances, loans, income and transaction activity.
DOCUMENT:\n{clipped}"""
    provider = "deterministic-parser"
    try:
        content, provider = await llm([{"role":"system","content":SYSTEM_PROMPT},{"role":"user","content":prompt}], json_mode=True)
        result = clean_json(content)
    except Exception as exc:
        log.warning("[LEPA-AI] LLM chain exhausted (%s). Using deterministic parser.", type(exc).__name__)
        result = deterministic_analysis(text, event_type)
    result.setdefault("obligations", [])
    result.setdefault("recommendations", [])
    result["provider"] = provider
    result["extractedCharacters"] = len(text)
    result["documentPreview"] = text[:900]
    log.info("[LEPA-AI] Analysis complete: event=%s obligations=%d recommendations=%d provider=%s", result.get("eventType"), len(result["obligations"]), len(result["recommendations"]), provider)
    return result


@app.post("/chat")
async def chat(req: ChatRequest):
    context = json.dumps(req.journey or {}, default=str)[:12000]
    messages = [
        {"role":"system","content":SYSTEM_PROMPT},
        {"role":"user","content":f"Current LEPA journey state:\n{context}\n\nCustomer question: {req.message}"},
    ]
    try:
        answer, provider = await llm(messages)
        return {"answer": answer.strip(), "provider": provider}
    except Exception:
        funding = (req.journey or {}).get("funding", {}) if req.journey else {}
        plan = (req.journey or {}).get("plan", {}) if req.journey else {}
        gap = funding.get("fundingGap", 0) if isinstance(funding, dict) else 0
        summary = plan.get("summary") if isinstance(plan, dict) else None
        answer = "I can help interpret this journey. "
        if summary:
            answer += str(summary) + " "
        if gap and float(gap) > 0:
            answer += f"The deterministic planner currently shows ₹{float(gap):,.0f} that needs to be arranged across upcoming deadlines. Follow the dated action plan before authorizing affected payments."
        else:
            answer += "The deterministic planner does not currently show an amount that must be arranged. Review the dated action plan and keep the protected reserve intact before authorizing payments."
        log.info("[LEPA-AI] Chat served by deterministic fallback")
        return {"answer": answer, "provider": "deterministic-fallback"}

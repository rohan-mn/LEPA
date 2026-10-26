# LEPA v5 — Life Event Payment Agent

LEPA v5 is a Docker-free Windows prototype of an AI-assisted personal financial journey planner.

The product no longer stops at extracting payments from a PDF. It combines verified document obligations with the customer's current bank position, active loans, expected income and observed spending pattern to create a dated action plan showing:

- what needs to be paid
- by when
- what should be reserved now
- how much additional money must be arranged
- what existing financial commitments must remain protected
- the projected balance after each step
- what happens next

AI explains and recommends. Deterministic services calculate balances, reserves, cash-flow gaps and payment state. The customer authorizes every payment.

## v5 flow

```text
Uploaded PDF / image / text
        ↓
Document extraction / OCR
        ↓
OpenRouter or Ollama
        ↓
Verified obligations + separate AI considerations
        ↓
LEPA Financial Planning Engine
        ├─ current liquid balance
        ├─ active loan EMIs
        ├─ expected salary / income
        ├─ observed monthly spending
        └─ protected planning reserve
        ↓
Dated Action Plan
        ├─ RESERVE FUNDS
        ├─ ARRANGE FUNDS
        ├─ PAY
        └─ CONFIRM MISSING DATE
        ↓
What Happens Next timeline
        ↓
Customer-authorized mock payment
        ↓
Automatic re-planning
```

## UI behavior

With no life event, the dashboard intentionally shows only the customer's bank balances and active loans plus an upload action.

After a document is uploaded, LEPA adds:

- active life-event header
- next payment
- money to arrange
- protected reserve
- projected end balance
- personal action plan
- dated "What happens next" timeline
- projected cash-flow chart
- financial context used by the planner
- recent money activity considered
- source obligations vs AI considerations
- controlled mock payment execution
- planner-aware AI Copilot

## AI provider routing

Set one variable in `.env`:

```env
AI_PROVIDER_PRIORITY=openrouter-first
```

means:

```text
OpenRouter → Ollama → deterministic fallback
```

or:

```env
AI_PROVIDER_PRIORITY=ollama-first
```

means:

```text
Ollama → OpenRouter → deterministic fallback
```

The AI terminal logs every provider attempt and the provider that handled the request.

## Windows prerequisites

Required:

- Java JDK 21
- Node.js 20+
- Python 3.11+

Maven does not need to be installed globally. The setup script downloads a portable Maven copy into the project when required.

Optional:

- Ollama — local LLM fallback / primary provider
- Tesseract + Poppler — OCR for scanned PDFs
- PostgreSQL — optional database profile

Docker, Kafka and Kubernetes are not required.

## First setup

Extract into a fresh folder such as:

```text
C:\Projects\lepa-native-windows-v5
```

Then run:

```text
setup-lepa.bat
```

The setup script:

1. creates the Python virtual environment
2. installs AI-service requirements
3. runs `npm install` and builds React
4. embeds the React build into Spring Boot
5. builds the backend JAR

Copy your OpenRouter key into `.env`:

```env
OPENROUTER_API_KEY=your_key_here
OPENROUTER_MODEL=openrouter/free
AI_PROVIDER_PRIORITY=openrouter-first
```

Then start:

```text
start-lepa.bat
```

Application:

```text
http://localhost:8080
```

## VS Code development mode

For full logs and hot reload, stop the BAT launcher and run three terminals.

### Terminal 1 — AI service

```powershell
cd ai-service
.\.venv\Scripts\python.exe -m uvicorn main:app --host 127.0.0.1 --port 8000 --reload --log-level debug
```

### Terminal 2 — Spring Boot

```powershell
cd backend
.\mvnw.cmd clean spring-boot:run
```

### Terminal 3 — React

```powershell
cd frontend
npm run dev
```

Open:

```text
http://localhost:5173
```

## Demo bank context

Defaults are configurable in `.env.example`:

```env
LEPA_BANK_SAVINGS_BALANCE=980000
LEPA_BANK_SALARY_BALANCE=300000
LEPA_BANK_LOAN_OUTSTANDING=675000
LEPA_BANK_LOAN_EMI=22450
LEPA_BANK_MONTHLY_INCOME=200000
LEPA_BANK_AVERAGE_MONTHLY_SPEND=110000
LEPA_BANK_RESERVE_BUFFER=50000
```

The protected reserve is calculated from observed monthly spend + active monthly EMI commitments + a planning buffer. It is advisory planning logic for the prototype, not regulated financial advice.

## Demo PDFs

Synthetic examples are included in `sample-docs/`:

- `home-loan-sanction-letter.pdf`
- `luxury-holiday-itinerary.pdf`

The financial-planning engine itself is generic. Any document that produces confirmed obligations with amounts/dates can be planned. The deterministic no-LLM fallback additionally contains safe generic line extraction plus optimized recognition for the supplied demos.

Only use synthetic/non-sensitive documents with external free-tier LLM services.

## Ollama

List downloaded models:

```powershell
ollama list
```

See currently loaded models:

```powershell
ollama ps
```

Download the default LEPA model:

```powershell
ollama pull qwen3:4b
```

Test it:

```powershell
ollama run qwen3:4b
```

If the Ollama HTTP server is not running:

```powershell
ollama serve
```

## Architecture boundary

```text
AI
│  understands documents, explains priorities, offers considerations
▼
Validated document facts
│
▼
Deterministic Financial Planning Engine
│  balances + loans + income + spending + deadlines
▼
Action Plan / Cash-flow Timeline
│
▼
Customer Authorization
│
▼
Mock Payment Service
```

A production bank implementation could later replace local H2, internal domain events and mock payment adapters with enterprise databases, Kafka/event streams, fraud/compliance controls and real payment rails without changing the LEPA planning concept.

package com.lepa.controller;

import com.lepa.service.AiServiceClient;
import com.lepa.service.JourneyService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {
    private final AiServiceClient ai;
    private final JourneyService journeys;
    public AiController(AiServiceClient ai, JourneyService journeys) { this.ai = ai; this.journeys = journeys; }

    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> analyze(@RequestPart("file") MultipartFile file,
                                       @RequestPart(value = "eventType", required = false) String eventType) throws IOException {
        return ai.analyze(file, eventType);
    }

    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody Map<String, Object> request) {
        Map<String, Object> payload = new LinkedHashMap<>(request);
        Object jid = request.get("journeyId");
        if (jid != null) {
            try { payload.put("journey", journeys.get(Long.valueOf(String.valueOf(jid)))); } catch (Exception ignored) {}
        }
        return ai.chat(payload);
    }

    @GetMapping("/health") public Map<String, Object> health() { return ai.health(); }
}

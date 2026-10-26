package com.lepa.controller;

import com.lepa.repository.JourneyEventRepository;
import com.lepa.repository.JourneyRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class SystemController {
    private final JourneyEventRepository events;
    private final JourneyRepository journeys;

    public SystemController(JourneyEventRepository events, JourneyRepository journeys) {
        this.events = events;
        this.journeys = journeys;
    }

    @GetMapping("/health") public Map<String,Object> health() {
        return Map.of("status","UP","service","lepa-backend","time", Instant.now().toString());
    }

    @PostMapping("/reset")
    @Transactional
    public Map<String,Object> reset() {
        events.deleteAllInBatch();
        journeys.deleteAll();
        return Map.of("status", "RESET", "message", "All life-event journeys and event history were cleared.");
    }
}

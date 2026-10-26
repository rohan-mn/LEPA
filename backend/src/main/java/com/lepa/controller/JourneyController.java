package com.lepa.controller;

import com.lepa.dto.JourneyDtos.CreateFromAnalysisRequest;
import com.lepa.dto.JourneyDtos.CreditRequest;
import com.lepa.dto.JourneyDtos.JourneyView;
import com.lepa.service.JourneyService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/journeys")
public class JourneyController {
    private final JourneyService service;
    public JourneyController(JourneyService service) { this.service = service; }

    @GetMapping public List<JourneyView> all() { return service.all(); }
    @GetMapping("/{id}") public JourneyView get(@PathVariable Long id) { return service.get(id); }
    @PostMapping("/from-analysis") public JourneyView create(@RequestBody CreateFromAnalysisRequest request) { return service.createFromAnalysis(request); }
    @PostMapping("/{id}/credit") public JourneyView credit(@PathVariable Long id, @RequestBody CreditRequest request) {
        if (request.amount() == null || request.amount().signum() <= 0) throw new IllegalArgumentException("Credit amount must be positive");
        return service.addCredit(id, request.amount());
    }
}

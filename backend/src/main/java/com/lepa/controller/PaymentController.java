package com.lepa.controller;

import com.lepa.dto.JourneyDtos.JourneyView;
import com.lepa.service.PaymentService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/milestones")
public class PaymentController {
    private final PaymentService service;
    public PaymentController(PaymentService service) { this.service = service; }
    @PostMapping("/{id}/execute") public JourneyView execute(@PathVariable Long id) { return service.execute(id); }
}

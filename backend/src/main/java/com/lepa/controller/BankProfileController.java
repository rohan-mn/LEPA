package com.lepa.controller;

import com.lepa.service.BankProfileService;
import com.lepa.service.BankProfileService.BankProfileView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bank-profile")
public class BankProfileController {
    private final BankProfileService service;

    public BankProfileController(BankProfileService service) {
        this.service = service;
    }

    @GetMapping
    public BankProfileView get() {
        return service.profile();
    }
}

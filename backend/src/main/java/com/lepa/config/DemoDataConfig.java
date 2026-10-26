package com.lepa.config;

import com.lepa.model.Journey;
import com.lepa.model.Milestone;
import com.lepa.repository.JourneyRepository;
import com.lepa.service.EventService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.LocalDate;

@Configuration
public class DemoDataConfig {
    @Value("${lepa.seed-demo-journey:false}")
    private boolean seedDemoJourney;

    @Bean
    CommandLineRunner demoData(JourneyRepository repo, EventService events) {
        return args -> {
            if (!seedDemoJourney || repo.count() > 0) return;
            Journey j = new Journey();
            j.setEventType("HOME_PURCHASE");
            j.setTitle("Greenfield Home Purchase");
            j.setCustomerName("Demo Customer");
            j.setStatus("ACTIVE");
            j.setAvailableFunds(new BigDecimal("1800000"));
            j.setExpectedIncome(new BigDecimal("200000"));
            j.addMilestone(m("Booking amount", "CONFIRMED", "500000", LocalDate.of(2026,10,12), "COMPLETED", "Builder agreement", "BANK_TRANSFER", "PAY-DEMO01"));
            j.addMilestone(m("Down payment", "CONFIRMED", "1500000", LocalDate.of(2026,11,10), "PENDING", "Builder agreement", "BANK_TRANSFER", null));
            j.addMilestone(m("Builder installment", "CONFIRMED", "1000000", LocalDate.of(2026,11,22), "PENDING", "Builder agreement", "BANK_TRANSFER", null));
            j.addMilestone(m("Registration reserve", "CONFIRMED", "600000", LocalDate.of(2026,11,30), "PENDING", "Agreement estimate", "BANK_TRANSFER", null));
            Journey saved = repo.save(j);
            events.emit(saved.getId(), "JOURNEY_CREATED", "Optional seeded home purchase journey initialized");
        };
    }

    private Milestone m(String name, String cat, String amount, LocalDate due, String status, String source, String rail, String paymentId) {
        Milestone m = new Milestone(); m.setName(name); m.setCategory(cat); m.setAmount(new BigDecimal(amount)); m.setDueDate(due);
        m.setStatus(status); m.setSource(source); m.setPaymentRail(rail); m.setPaymentId(paymentId); return m;
    }
}

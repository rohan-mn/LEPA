package com.lepa.service;

import com.lepa.dto.JourneyDtos.JourneyView;
import com.lepa.model.Journey;
import com.lepa.model.Milestone;
import com.lepa.repository.JourneyRepository;
import com.lepa.repository.MilestoneRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private final MilestoneRepository milestones;
    private final JourneyRepository journeys;
    private final JourneyService journeyService;
    private final EventService events;

    public PaymentService(MilestoneRepository milestones, JourneyRepository journeys, JourneyService journeyService, EventService events) {
        this.milestones = milestones; this.journeys = journeys; this.journeyService = journeyService; this.events = events;
    }

    @Transactional
    public JourneyView execute(Long milestoneId) {
        Milestone m = milestones.findById(milestoneId).orElseThrow(() -> new EntityNotFoundException("Milestone not found"));
        if (!"CONFIRMED".equals(m.getCategory())) throw new IllegalArgumentException("Recommended items cannot be executed as confirmed payments");
        if ("COMPLETED".equals(m.getStatus())) return journeyService.toView(m.getJourney());
        Journey j = m.getJourney();
        BigDecimal amount = m.getAmount() == null ? BigDecimal.ZERO : m.getAmount();
        if (amount.signum() <= 0) throw new IllegalArgumentException("Payment amount must be greater than zero");
        log.info("[LEPA-PAYMENT] Requested milestone={} amount={} availableFunds={}", m.getName(), amount, j.getAvailableFunds());
        if (j.getAvailableFunds().compareTo(amount) < 0) {
            log.warn("[LEPA-PAYMENT] Validation failed: insufficient funds");
            throw new IllegalStateException("Insufficient currently available funds for this mock payment");
        }

        m.setStatus("COMPLETED");
        m.setPaymentId("PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        j.setAvailableFunds(j.getAvailableFunds().subtract(amount));
        milestones.save(m); journeys.save(j);
        log.info("[LEPA-PAYMENT] Completed paymentId={} newAvailableFunds={}", m.getPaymentId(), j.getAvailableFunds());
        events.emit(j.getId(), "PAYMENT_COMPLETED", m.getName() + " paid via " + m.getPaymentRail() + " (mock)");
        boolean done = j.getMilestones().stream().filter(x -> "CONFIRMED".equals(x.getCategory())).allMatch(x -> "COMPLETED".equals(x.getStatus()));
        if (done) { j.setStatus("COMPLETED"); journeys.save(j); events.emit(j.getId(), "JOURNEY_COMPLETED", j.getTitle() + " completed"); }
        return journeyService.toView(j);
    }
}

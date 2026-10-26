package com.lepa.service;

import com.lepa.dto.JourneyDtos.*;
import com.lepa.model.Journey;
import com.lepa.model.Milestone;
import com.lepa.repository.JourneyRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
public class JourneyService {
    private static final Logger log = LoggerFactory.getLogger(JourneyService.class);
    private final JourneyRepository journeys;
    private final FundingService funding;
    private final EventService events;
    private final PlanningService planning;

    public JourneyService(JourneyRepository journeys, FundingService funding, EventService events, PlanningService planning) {
        this.journeys = journeys; this.funding = funding; this.events = events; this.planning = planning;
    }

    public List<JourneyView> all() {
        return journeys.findAll().stream().sorted(Comparator.comparing(Journey::getId).reversed()).map(this::toView).toList();
    }

    public Journey getEntity(Long id) {
        return journeys.findById(id).orElseThrow(() -> new EntityNotFoundException("Journey not found: " + id));
    }

    public JourneyView get(Long id) { return toView(getEntity(id)); }

    @Transactional
    public JourneyView createFromAnalysis(CreateFromAnalysisRequest req) {
        Journey j = new Journey();
        j.setEventType(blank(req.eventType()) ? "CUSTOM" : req.eventType());
        j.setTitle(blank(req.title()) ? pretty(j.getEventType()) + " Journey" : req.title());
        j.setCustomerName(blank(req.customerName()) ? "Demo Customer" : req.customerName());
        j.setStatus("ACTIVE");
        j.setAvailableFunds(nvl(req.availableFunds(), new BigDecimal("1800000")));
        j.setExpectedIncome(nvl(req.expectedIncome(), new BigDecimal("200000")));

        if (req.obligations() != null) req.obligations().forEach(o -> j.addMilestone(milestone(
                o.name(), "CONFIRMED", o.amount(), o.dueDate(), Boolean.TRUE.equals(o.alreadyPaid()) ? "COMPLETED" : "PENDING", o.source(), o.paymentRail())));
        if (req.recommendations() != null) req.recommendations().forEach(r -> j.addMilestone(milestone(
                r.name(), "RECOMMENDED", r.amount(), r.dueDate(), "PENDING", r.source(), null)));

        Journey saved = journeys.save(j);
        var calc = funding.calculate(saved);
        log.info("[LEPA] Journey created id={} event={} obligations={} availableFunds={} upcoming={} fundingGap={}", saved.getId(), saved.getEventType(), saved.getMilestones().size(), saved.getAvailableFunds(), calc.upcomingConfirmed(), calc.fundingGap());
        events.emit(saved.getId(), "JOURNEY_CREATED", saved.getTitle() + " created with " + saved.getMilestones().size() + " milestones");
        events.emit(saved.getId(), "PLAN_GENERATED", "LEPA created a dated action plan from document obligations and current financial context");
        if (calc.fundingGap().signum() > 0) {
            events.emit(saved.getId(), "FUNDING_RISK_IDENTIFIED", "Potential funding gap detected");
        }
        return toView(saved);
    }

    @Transactional
    public JourneyView addCredit(Long id, BigDecimal amount) {
        Journey j = getEntity(id);
        j.setAvailableFunds(j.getAvailableFunds().add(amount));
        journeys.save(j);
        events.emit(id, "ACCOUNT_CREDITED", "Simulated account credit of ₹" + amount.toPlainString());
        if (funding.calculate(j).fundingGap().signum() == 0) {
            events.emit(id, "FUNDING_RISK_RESOLVED", "Projected funding gap is now resolved");
        }
        return toView(j);
    }

    public JourneyView toView(Journey j) {
        List<MilestoneView> ms = j.getMilestones().stream().map(m -> new MilestoneView(
                m.getId(), m.getName(), m.getCategory(), m.getAmount(), m.getDueDate(), m.getStatus(),
                m.getSource(), m.getPaymentRail(), m.getPaymentId())).toList();
        long actionable = ms.stream().filter(m -> "CONFIRMED".equals(m.category())).count();
        long complete = ms.stream().filter(m -> "CONFIRMED".equals(m.category()) && "COMPLETED".equals(m.status())).count();
        int progress = actionable == 0 ? 0 : (int)Math.round(100.0 * complete / actionable);
        return new JourneyView(j.getId(), j.getEventType(), j.getTitle(), j.getStatus(), j.getCustomerName(),
                j.getCreatedAt(), j.getUpdatedAt(), ms, funding.calculate(j), planning.calculate(j), progress);
    }

    private Milestone milestone(String name, String category, BigDecimal amount, LocalDate dueDate, String status, String source, String rail) {
        Milestone m = new Milestone();
        m.setName(blank(name) ? "Payment milestone" : name); m.setCategory(category); m.setAmount(amount);
        m.setDueDate(dueDate); m.setStatus(status); m.setSource(blank(source) ? ("CONFIRMED".equals(category) ? "Document" : "AI recommendation") : source);
        m.setPaymentRail(blank(rail) ? ("CONFIRMED".equals(category) ? "BANK_TRANSFER" : null) : rail);
        return m;
    }
    private boolean blank(String s) { return s == null || s.isBlank(); }
    private <T> T nvl(T v, T fallback) { return v == null ? fallback : v; }
    private String pretty(String s) { return s == null ? "Life Event" : s.replace('_',' ').toLowerCase(); }
}

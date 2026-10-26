package com.lepa.service;

import com.lepa.dto.JourneyDtos.FundingView;
import com.lepa.model.Journey;
import com.lepa.model.Milestone;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class FundingService {
    private final PlanningService planning;

    public FundingService(PlanningService planning) {
        this.planning = planning;
    }

    public FundingView calculate(Journey j) {
        BigDecimal upcoming = j.getMilestones().stream()
                .filter(m -> "CONFIRMED".equals(m.getCategory()))
                .filter(m -> !"COMPLETED".equals(m.getStatus()))
                .map(Milestone::getAmount)
                .filter(a -> a != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        var plan = planning.calculate(j);
        BigDecimal available = safe(j.getAvailableFunds());
        BigDecimal expected = safe(plan.expectedMonthlyIncome());
        BigDecimal projected = safe(plan.projectedEndBalance());
        BigDecimal gap = safe(plan.totalToArrange());

        String risk;
        if (gap.signum() > 0) {
            risk = "HIGH";
        } else if (projected.compareTo(plan.protectedReserve()) < 0) {
            risk = "MEDIUM";
        } else {
            risk = "LOW";
        }

        return new FundingView(available, expected, upcoming, projected, gap, risk);
    }

    private BigDecimal safe(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}

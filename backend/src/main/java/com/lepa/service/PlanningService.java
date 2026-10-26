package com.lepa.service;

import com.lepa.dto.JourneyDtos.ActionStepView;
import com.lepa.dto.JourneyDtos.CashFlowPointView;
import com.lepa.dto.JourneyDtos.PlanningView;
import com.lepa.model.Journey;
import com.lepa.model.Milestone;
import com.lepa.service.BankProfileService.BankProfileView;
import com.lepa.service.BankProfileService.LoanView;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class PlanningService {
    private static final BigDecimal DAYS_PER_MONTH = new BigDecimal("30");
    private final BankProfileService bankProfiles;

    public PlanningService(BankProfileService bankProfiles) {
        this.bankProfiles = bankProfiles;
    }

    public PlanningView calculate(Journey journey) {
        BankProfileView bank = bankProfiles.profile();
        LocalDate today = LocalDate.now();
        BigDecimal balance = safe(journey.getAvailableFunds());
        BigDecimal reserve = safe(bank.planningReserve());
        BigDecimal monthlySpend = safe(bank.averageMonthlySpend());
        BigDecimal monthlyIncome = safe(bank.expectedMonthlyIncome());
        BigDecimal activeEmis = bank.loans().stream().map(LoanView::emi).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal spendableNow = positive(balance.subtract(reserve));

        List<Milestone> pending = journey.getMilestones().stream()
                .filter(m -> "CONFIRMED".equals(m.getCategory()))
                .filter(m -> !"COMPLETED".equals(m.getStatus()))
                .sorted(Comparator.comparing(Milestone::getDueDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Milestone::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        List<ActionStepView> actions = new ArrayList<>();
        List<CashFlowPointView> cashFlow = new ArrayList<>();
        cashFlow.add(new CashFlowPointView(today, "Current liquid balance", balance, BigDecimal.ZERO, balance, "OPENING_BALANCE"));

        int sequence = 1;
        for (Milestone completed : journey.getMilestones().stream()
                .filter(m -> "CONFIRMED".equals(m.getCategory()))
                .filter(m -> "COMPLETED".equals(m.getStatus()))
                .sorted(Comparator.comparing(Milestone::getDueDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList()) {
            actions.add(new ActionStepView(sequence++, "COMPLETED", completed.getName() + " completed",
                    "This confirmed obligation is already settled and is not included again in the forward cash-flow plan.",
                    completed.getDueDate(), completed.getDueDate(), completed.getAmount(), BigDecimal.ZERO,
                    null, "COMPLETED", "DONE", completed.getId()));
        }

        LocalDate cursor = today;
        LocalDate nextSalary = bank.nextIncomeDate();
        List<LoanCursor> loanCursors = bank.loans().stream()
                .map(l -> new LoanCursor(l, l.nextDueDate()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        BigDecimal totalArrange = BigDecimal.ZERO;
        LocalDate nextCritical = null;

        for (Milestone m : pending) {
            BigDecimal amount = safe(m.getAmount());
            LocalDate due = m.getDueDate();

            if (due == null) {
                actions.add(new ActionStepView(sequence++, "REVIEW_DATE", "Confirm the deadline for " + m.getName(),
                        "The document contains an obligation but no reliable due date. Confirm the deadline before scheduling or authorizing payment.",
                        null, null, amount, BigDecimal.ZERO, balance, "DATE_REQUIRED", "HIGH", m.getId()));
                continue;
            }

            if (due.isBefore(today)) due = today;

            // Add salary credits that occur before this obligation.
            while (nextSalary != null && !nextSalary.isAfter(due)) {
                balance = balance.add(monthlyIncome);
                cashFlow.add(new CashFlowPointView(nextSalary, "Expected salary / income", monthlyIncome,
                        BigDecimal.ZERO, balance, "INCOME"));
                nextSalary = nextMonthly(nextSalary, bank.nextIncomeDate().getDayOfMonth());
            }

            // Add existing loan EMIs that fall before this obligation.
            for (LoanCursor lc : loanCursors) {
                while (lc.nextDue != null && !lc.nextDue.isAfter(due)) {
                    BigDecimal emi = safe(lc.loan.emi());
                    balance = balance.subtract(emi);
                    cashFlow.add(new CashFlowPointView(lc.nextDue, lc.loan.name() + " EMI", BigDecimal.ZERO,
                            emi, balance, "EXISTING_LOAN"));
                    lc.nextDue = nextMonthly(lc.nextDue, lc.loan.nextDueDate().getDayOfMonth());
                }
            }

            // Estimate essential day-to-day outflow between action checkpoints from recent activity.
            long days = Math.max(0, ChronoUnit.DAYS.between(cursor, due));
            if (days > 0 && monthlySpend.signum() > 0) {
                BigDecimal essential = monthlySpend
                        .multiply(BigDecimal.valueOf(days))
                        .divide(DAYS_PER_MONTH, 0, RoundingMode.HALF_UP);
                balance = balance.subtract(essential);
                cashFlow.add(new CashFlowPointView(due, "Estimated essential spending until deadline",
                        BigDecimal.ZERO, essential, balance, "ESSENTIAL_SPEND"));
            }
            cursor = due;

            BigDecimal requiredToPreserveReserve = positive(amount.add(reserve).subtract(balance));
            String priority = priority(today, due, requiredToPreserveReserve);

            if (requiredToPreserveReserve.signum() > 0) {
                LocalDate arrangeBy = due.minusDays(5).isAfter(today) ? due.minusDays(5) : today;
                totalArrange = totalArrange.add(requiredToPreserveReserve);
                if (nextCritical == null || arrangeBy.isBefore(nextCritical)) nextCritical = arrangeBy;

                actions.add(new ActionStepView(sequence++, "ARRANGE_FUNDS",
                        "Arrange " + inr(requiredToPreserveReserve) + " for " + m.getName(),
                        "Projected liquidity would otherwise fall below the protected planning reserve of " + inr(reserve) + ".",
                        arrangeBy, due, amount, requiredToPreserveReserve, balance,
                        "ACTION_REQUIRED", priority, m.getId()));

                // The forward plan assumes the customer follows LEPA's arrangement step.
                balance = balance.add(requiredToPreserveReserve);
                cashFlow.add(new CashFlowPointView(arrangeBy, "Funds to arrange for " + m.getName(),
                        requiredToPreserveReserve, BigDecimal.ZERO, balance, "ARRANGE_FUNDS"));
            } else if (ChronoUnit.DAYS.between(today, due) > 3) {
                LocalDate reserveBy = due.minusDays(7).isAfter(today) ? due.minusDays(7) : today;
                actions.add(new ActionStepView(sequence++, "RESERVE_FUNDS",
                        "Reserve " + inr(amount) + " for " + m.getName(),
                        "The obligation is currently fundable. Ring-fencing the amount reduces the risk of spending it before the deadline.",
                        reserveBy, due, amount, BigDecimal.ZERO, balance,
                        "READY", priority, m.getId()));
            }

            balance = balance.subtract(amount);
            cashFlow.add(new CashFlowPointView(due, m.getName(), BigDecimal.ZERO, amount, balance, "LIFE_EVENT_PAYMENT"));
            actions.add(new ActionStepView(sequence++, "PAY",
                    "Pay " + m.getName(),
                    requiredToPreserveReserve.signum() > 0
                            ? "Make this payment after the required funds have been arranged."
                            : "This payment fits the current plan while the protected reserve is maintained.",
                    due, due, amount, requiredToPreserveReserve, balance,
                    requiredToPreserveReserve.signum() > 0 ? "READY_AFTER_ARRANGEMENT" : "READY",
                    priority, m.getId()));

            if (nextCritical == null || due.isBefore(nextCritical)) nextCritical = due;
        }

        BigDecimal projectedEnd = balance;
        String summary;
        if (pending.isEmpty()) {
            summary = "There are no pending confirmed obligations. LEPA will continue to monitor the journey for new information.";
        } else if (totalArrange.signum() > 0) {
            summary = "Arrange " + inr(totalArrange) + " across the upcoming deadlines to complete the confirmed obligations while keeping "
                    + inr(reserve) + " protected for existing commitments and normal spending.";
        } else {
            summary = "All confirmed obligations are currently fundable while keeping " + inr(reserve)
                    + " protected for existing commitments and normal spending.";
        }

        return new PlanningView(
                safe(journey.getAvailableFunds()), reserve, activeEmis, monthlySpend, monthlyIncome,
                bank.nextIncomeDate(), spendableNow, totalArrange, projectedEnd, nextCritical,
                summary, actions, cashFlow
        );
    }

    private String priority(LocalDate today, LocalDate due, BigDecimal shortfall) {
        long days = ChronoUnit.DAYS.between(today, due);
        if (days <= 7) return "CRITICAL";
        if (shortfall.signum() > 0 || days <= 21) return "HIGH";
        return "NORMAL";
    }

    private LocalDate nextMonthly(LocalDate current, int preferredDay) {
        YearMonth ym = YearMonth.from(current).plusMonths(1);
        return ym.atDay(Math.min(preferredDay, ym.lengthOfMonth()));
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal positive(BigDecimal value) {
        return value.signum() > 0 ? value : BigDecimal.ZERO;
    }

    private String inr(BigDecimal amount) {
        if (amount == null) return "₹0";
        return "₹" + String.format(java.util.Locale.ENGLISH, "%,.0f", amount);
    }

    private static final class LoanCursor {
        private final LoanView loan;
        private LocalDate nextDue;
        private LoanCursor(LoanView loan, LocalDate nextDue) {
            this.loan = loan;
            this.nextDue = nextDue;
        }
    }
}

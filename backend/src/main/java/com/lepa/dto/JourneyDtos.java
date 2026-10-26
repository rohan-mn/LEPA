package com.lepa.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public class JourneyDtos {
    public record MilestoneView(Long id, String name, String category, BigDecimal amount, LocalDate dueDate,
                                String status, String source, String paymentRail, String paymentId) {}

    public record FundingView(BigDecimal availableFunds, BigDecimal expectedIncome, BigDecimal upcomingConfirmed,
                              BigDecimal projectedBalance, BigDecimal fundingGap, String riskLevel) {}

    public record ActionStepView(Integer sequence, String type, String title, String rationale,
                                 LocalDate actionDate, LocalDate paymentDueDate, BigDecimal amount,
                                 BigDecimal amountToArrange, BigDecimal projectedBalanceAfter,
                                 String status, String priority, Long milestoneId) {}

    public record CashFlowPointView(LocalDate date, String label, BigDecimal inflow, BigDecimal outflow,
                                    BigDecimal projectedBalance, String kind) {}

    public record PlanningView(BigDecimal startingLiquidity, BigDecimal protectedReserve,
                               BigDecimal activeMonthlyEmis, BigDecimal averageMonthlySpend,
                               BigDecimal expectedMonthlyIncome, LocalDate nextIncomeDate,
                               BigDecimal spendableNow, BigDecimal totalToArrange,
                               BigDecimal projectedEndBalance, LocalDate nextCriticalDate,
                               String summary, List<ActionStepView> actions,
                               List<CashFlowPointView> cashFlow) {}

    public record JourneyView(Long id, String eventType, String title, String status, String customerName,
                              Instant createdAt, Instant updatedAt, List<MilestoneView> milestones,
                              FundingView funding, PlanningView plan, int progressPercent) {}

    public record ObligationInput(String name, BigDecimal amount, LocalDate dueDate, String source,
                                  String paymentRail, Boolean alreadyPaid) {}
    public record RecommendationInput(String name, BigDecimal amount, LocalDate dueDate, String source) {}

    public record CreateFromAnalysisRequest(String eventType, String title, String customerName,
                                            BigDecimal availableFunds, BigDecimal expectedIncome,
                                            List<ObligationInput> obligations,
                                            List<RecommendationInput> recommendations) {}

    public record ChatRequest(Long journeyId, String message) {}
    public record CreditRequest(BigDecimal amount) {}
}

package com.lepa.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Service
public class BankProfileService {
    @Value("${lepa.demo.savings-balance:980000}") private BigDecimal savingsBalance;
    @Value("${lepa.demo.salary-balance:300000}") private BigDecimal salaryBalance;
    @Value("${lepa.demo.loan-outstanding:675000}") private BigDecimal loanOutstanding;
    @Value("${lepa.demo.loan-emi:22450}") private BigDecimal loanEmi;
    @Value("${lepa.demo.monthly-income:200000}") private BigDecimal monthlyIncome;
    @Value("${lepa.demo.average-monthly-spend:110000}") private BigDecimal averageMonthlySpend;
    @Value("${lepa.demo.reserve-buffer:50000}") private BigDecimal reserveBuffer;

    public record AccountView(String name, String type, String maskedNumber, BigDecimal balance) {}
    public record LoanView(String name, String maskedNumber, BigDecimal outstanding, BigDecimal emi,
                           BigDecimal interestRate, LocalDate nextDueDate, Integer remainingMonths) {}
    public record TransactionView(LocalDate date, String description, String category, BigDecimal amount, String direction) {}
    public record BankProfileView(String customerName, BigDecimal totalLiquidBalance,
                                  List<AccountView> accounts, List<LoanView> loans,
                                  BigDecimal expectedMonthlyIncome, LocalDate nextIncomeDate,
                                  BigDecimal averageMonthlySpend, BigDecimal planningReserve,
                                  List<TransactionView> recentTransactions) {}

    public BankProfileView profile() {
        LocalDate today = LocalDate.now();
        LocalDate nextIncome = nextOccurrence(today, 31);
        LocalDate nextLoanDue = today.plusDays(12);

        var accounts = List.of(
                new AccountView("Primary Savings", "SAVINGS", "•••• 1842", savingsBalance),
                new AccountView("Salary Account", "SALARY", "•••• 7621", salaryBalance)
        );
        var loans = loanOutstanding.signum() > 0
                ? List.of(new LoanView("Auto Loan", "•••• 4318", loanOutstanding, loanEmi,
                    new BigDecimal("9.20"), nextLoanDue, 31))
                : List.<LoanView>of();

        BigDecimal activeEmis = loans.stream().map(LoanView::emi).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal reserve = averageMonthlySpend.add(activeEmis).add(reserveBuffer);

        var transactions = List.of(
                new TransactionView(today.minusDays(2), "Groceries & household", "ESSENTIAL_SPEND", new BigDecimal("23500"), "DEBIT"),
                new TransactionView(today.minusDays(5), "Utilities & subscriptions", "ESSENTIAL_SPEND", new BigDecimal("12000"), "DEBIT"),
                new TransactionView(today.minusDays(9), "Auto-loan EMI", "LOAN_EMI", loanEmi, "DEBIT"),
                new TransactionView(today.minusDays(13), "Card & discretionary spend", "DISCRETIONARY", new BigDecimal("18000"), "DEBIT"),
                new TransactionView(today.minusDays(19), "Family transfer", "TRANSFER", new BigDecimal("40000"), "DEBIT"),
                new TransactionView(today.minusDays(25), "Salary credit", "INCOME", monthlyIncome, "CREDIT")
        );

        return new BankProfileView(
                "Demo Customer",
                savingsBalance.add(salaryBalance),
                accounts,
                loans,
                monthlyIncome,
                nextIncome,
                averageMonthlySpend,
                reserve,
                transactions
        );
    }

    private LocalDate nextOccurrence(LocalDate today, int preferredDay) {
        YearMonth ym = YearMonth.from(today);
        LocalDate candidate = ym.atDay(Math.min(preferredDay, ym.lengthOfMonth()));
        if (!candidate.isAfter(today)) {
            ym = ym.plusMonths(1);
            candidate = ym.atDay(Math.min(preferredDay, ym.lengthOfMonth()));
        }
        return candidate;
    }
}

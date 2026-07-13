package com.example.backend.domain.financials;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FinancialProjectionSummary(
    LocalDate payPeriodStart,
    LocalDate payPeriodEnd,
    int monthlyBillCount,
    int annualWithdrawalCount,
    int assetAccountCount,
    int debtAccountCount,
    int incomeSummaryItemCount,
    int incomeEventCount,
    int importantDateCount,
    BigDecimal totalMonthlyExpenses,
    BigDecimal totalAnnualWithdrawals,
    BigDecimal totalTrackedAssets,
    BigDecimal totalDebt,
    BigDecimal netWorth) {

  public FinancialProjectionSummary {
    totalMonthlyExpenses = zeroIfNull(totalMonthlyExpenses);
    totalAnnualWithdrawals = zeroIfNull(totalAnnualWithdrawals);
    totalTrackedAssets = zeroIfNull(totalTrackedAssets);
    totalDebt = zeroIfNull(totalDebt);
    netWorth = zeroIfNull(netWorth);
  }

  private static BigDecimal zeroIfNull(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }
}

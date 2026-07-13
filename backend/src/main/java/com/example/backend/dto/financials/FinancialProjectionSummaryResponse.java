package com.example.backend.dto.financials;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FinancialProjectionSummaryResponse(
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
    BigDecimal netWorth) {}

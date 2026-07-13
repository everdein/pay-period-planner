package com.example.backend.dto.financials;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ExpenseSnapshotResponse(
    long version,
    LocalDate payPeriodStart,
    LocalDate payPeriodEnd,
    BigDecimal totalMonthlyExpenses,
    BigDecimal paidTotal,
    BigDecimal unpaidTotal,
    BigDecimal payPeriodTotal,
    BigDecimal totalAnnualWithdrawals,
    BigDecimal annualPayPeriodTotal,
    BigDecimal totalTrackedAssets,
    BigDecimal totalDebt,
    BigDecimal netWorth,
    List<AssetCategoryResponse> assetCategories,
    List<DebtAccountResponse> debtAccounts,
    List<IncomeSummaryItemResponse> incomeSummaryItems,
    List<ExpenseBillResponse> bills,
    List<AnnualWithdrawalResponse> annualWithdrawals,
    List<IncomeEventResponse> incomeEvents,
    List<ImportantDateResponse> importantDates) {}

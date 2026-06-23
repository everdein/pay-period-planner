package com.example.backend.dto.financials;

import java.time.LocalDate;
import java.util.List;

public record ExpenseSnapshotResponse(
    LocalDate payPeriodStart,
    LocalDate payPeriodEnd,
    double totalMonthlyExpenses,
    double paidTotal,
    double unpaidTotal,
    double payPeriodTotal,
    double totalAnnualWithdrawals,
    double annualPayPeriodTotal,
    double totalTrackedAssets,
    double totalDebt,
    double netWorth,
    List<AssetCategoryResponse> assetCategories,
    List<DebtAccountResponse> debtAccounts,
    List<IncomeSummaryItemResponse> incomeSummaryItems,
    List<ExpenseBillResponse> bills,
    List<AnnualWithdrawalResponse> annualWithdrawals,
    List<IncomeEventResponse> incomeEvents,
    List<ImportantDateResponse> importantDates) {}

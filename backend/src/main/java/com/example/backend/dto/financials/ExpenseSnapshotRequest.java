package com.example.backend.dto.financials;

import java.time.LocalDate;
import java.util.List;

public record ExpenseSnapshotRequest(
    LocalDate payPeriodStart,
    LocalDate payPeriodEnd,
    List<ExpenseBillSnapshotRequest> bills,
    List<AnnualWithdrawalSnapshotRequest> annualWithdrawals,
    List<AssetCategorySnapshotRequest> assetCategories,
    List<DebtAccountSnapshotRequest> debtAccounts,
    List<IncomeSummaryItemSnapshotRequest> incomeSummaryItems,
    List<IncomeEventSnapshotRequest> incomeEvents,
    List<ImportantDateSnapshotRequest> importantDates) {}

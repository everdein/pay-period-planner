package com.example.backend.domain.migration;

import com.example.backend.repository.FinancialsData;

public record FinancialSnapshotCounts(
    long monthlyBills,
    long annualWithdrawals,
    long assetAccounts,
    long debtAccounts,
    long incomeSummaryItems,
    long incomeEvents,
    long importantDates,
    long auditEvents) {

  public static FinancialSnapshotCounts from(FinancialsData data) {
    return new FinancialSnapshotCounts(
        data.bills().size(),
        data.annualWithdrawals().size(),
        data.assetAccounts().size(),
        data.debtAccounts().size(),
        data.incomeSummaryItems().size(),
        data.incomeEvents().size(),
        data.importantDates().size(),
        data.auditEvents().size());
  }
}

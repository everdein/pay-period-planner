package com.example.backend.domain.migration;

import com.example.backend.domain.financials.FinancialSnapshot;
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

  public static FinancialSnapshotCounts from(FinancialSnapshot snapshot, long auditEvents) {
    return new FinancialSnapshotCounts(
        snapshot.bills().size(),
        snapshot.annualWithdrawals().size(),
        snapshot.assetAccounts().size(),
        snapshot.debtAccounts().size(),
        snapshot.incomeSummaryItems().size(),
        snapshot.incomeEvents().size(),
        snapshot.importantDates().size(),
        auditEvents);
  }
}

package com.example.backend.dto.migration;

import com.example.backend.domain.migration.FinancialSnapshotCounts;

public record FinancialSnapshotCountsResponse(
    long monthlyBills,
    long annualWithdrawals,
    long assetAccounts,
    long debtAccounts,
    long incomeSummaryItems,
    long incomeEvents,
    long importantDates,
    long auditEvents) {

  public static FinancialSnapshotCountsResponse from(FinancialSnapshotCounts counts) {
    return new FinancialSnapshotCountsResponse(
        counts.monthlyBills(),
        counts.annualWithdrawals(),
        counts.assetAccounts(),
        counts.debtAccounts(),
        counts.incomeSummaryItems(),
        counts.incomeEvents(),
        counts.importantDates(),
        counts.auditEvents());
  }
}

package com.example.backend.repository;

import com.example.backend.domain.financials.AnnualWithdrawal;
import com.example.backend.domain.financials.AssetAccount;
import com.example.backend.domain.financials.DebtAccount;
import com.example.backend.domain.financials.ExpenseBill;
import com.example.backend.domain.financials.FinancialAuditEvent;
import com.example.backend.domain.financials.FinancialSnapshot;
import com.example.backend.domain.financials.ImportantDate;
import com.example.backend.domain.financials.IncomeEvent;
import com.example.backend.domain.financials.IncomeSummaryItem;
import java.time.LocalDate;
import java.util.List;

public record FinancialsData(
    Long version,
    LocalDate payPeriodStart,
    LocalDate payPeriodEnd,
    List<ExpenseBill> bills,
    List<AnnualWithdrawal> annualWithdrawals,
    List<AssetAccount> assetAccounts,
    List<DebtAccount> debtAccounts,
    List<IncomeSummaryItem> incomeSummaryItems,
    List<IncomeEvent> incomeEvents,
    List<ImportantDate> importantDates,
    List<FinancialAuditEvent> auditEvents) {

  public FinancialsData {
    if (version == null || version < 1) {
      version = 1L;
    }

    bills = immutableOrEmpty(bills);
    annualWithdrawals = immutableOrEmpty(annualWithdrawals);
    assetAccounts = immutableOrEmpty(assetAccounts);
    debtAccounts = immutableOrEmpty(debtAccounts);
    incomeSummaryItems = immutableOrEmpty(incomeSummaryItems);
    incomeEvents = immutableOrEmpty(incomeEvents);
    importantDates = immutableOrEmpty(importantDates);
    auditEvents = immutableOrEmpty(auditEvents);
  }

  public FinancialsData(
      LocalDate payPeriodStart,
      LocalDate payPeriodEnd,
      List<ExpenseBill> bills,
      List<AnnualWithdrawal> annualWithdrawals,
      List<AssetAccount> assetAccounts,
      List<DebtAccount> debtAccounts,
      List<IncomeSummaryItem> incomeSummaryItems,
      List<IncomeEvent> incomeEvents,
      List<ImportantDate> importantDates) {
    this(
        1L,
        payPeriodStart,
        payPeriodEnd,
        bills,
        annualWithdrawals,
        assetAccounts,
        debtAccounts,
        incomeSummaryItems,
        incomeEvents,
        importantDates,
        List.of());
  }

  public static FinancialsData empty() {
    return new FinancialsData(
        1L,
        LocalDate.now().withDayOfMonth(1),
        LocalDate.now().withDayOfMonth(15),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }

  public FinancialsData withVersion(long version) {
    return new FinancialsData(
        version,
        payPeriodStart,
        payPeriodEnd,
        bills,
        annualWithdrawals,
        assetAccounts,
        debtAccounts,
        incomeSummaryItems,
        incomeEvents,
        importantDates,
        auditEvents);
  }

  public FinancialSnapshot toSnapshot() {
    return new FinancialSnapshot(
        version,
        payPeriodStart,
        payPeriodEnd,
        bills,
        annualWithdrawals,
        assetAccounts,
        debtAccounts,
        incomeSummaryItems,
        incomeEvents,
        importantDates);
  }

  public static FinancialsData fromSnapshot(FinancialSnapshot snapshot) {
    return fromSnapshot(snapshot, List.of());
  }

  public static FinancialsData fromSnapshot(
      FinancialSnapshot snapshot, List<FinancialAuditEvent> auditEvents) {
    return new FinancialsData(
        snapshot.version(),
        snapshot.payPeriodStart(),
        snapshot.payPeriodEnd(),
        snapshot.bills(),
        snapshot.annualWithdrawals(),
        snapshot.assetAccounts(),
        snapshot.debtAccounts(),
        snapshot.incomeSummaryItems(),
        snapshot.incomeEvents(),
        snapshot.importantDates(),
        auditEvents);
  }

  private static <T> List<T> immutableOrEmpty(List<T> value) {
    return value == null ? List.of() : List.copyOf(value);
  }
}

package com.example.backend.domain.financials;

import java.time.LocalDate;
import java.util.List;

public record FinancialSnapshot(
    long version,
    LocalDate payPeriodStart,
    LocalDate payPeriodEnd,
    FinancialPlanningSettings planningSettings,
    FinancialProjectionRoles projectionRoles,
    List<ExpenseBill> bills,
    List<AnnualWithdrawal> annualWithdrawals,
    List<AssetAccount> assetAccounts,
    List<DebtAccount> debtAccounts,
    List<IncomeSummaryItem> incomeSummaryItems,
    List<IncomeEvent> incomeEvents,
    List<ImportantDate> importantDates) {

  public FinancialSnapshot {
    bills = immutableOrEmpty(bills);
    annualWithdrawals = immutableOrEmpty(annualWithdrawals);
    assetAccounts = immutableOrEmpty(assetAccounts);
    debtAccounts = immutableOrEmpty(debtAccounts);
    incomeSummaryItems = immutableOrEmpty(incomeSummaryItems);
    incomeEvents = immutableOrEmpty(incomeEvents);
    importantDates = immutableOrEmpty(importantDates);
  }

  public FinancialSnapshot(
      long version,
      LocalDate payPeriodStart,
      LocalDate payPeriodEnd,
      FinancialProjectionRoles projectionRoles,
      List<ExpenseBill> bills,
      List<AnnualWithdrawal> annualWithdrawals,
      List<AssetAccount> assetAccounts,
      List<DebtAccount> debtAccounts,
      List<IncomeSummaryItem> incomeSummaryItems,
      List<IncomeEvent> incomeEvents,
      List<ImportantDate> importantDates) {
    this(
        version,
        payPeriodStart,
        payPeriodEnd,
        null,
        projectionRoles,
        bills,
        annualWithdrawals,
        assetAccounts,
        debtAccounts,
        incomeSummaryItems,
        incomeEvents,
        importantDates);
  }

  public FinancialSnapshot(
      long version,
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
        version,
        payPeriodStart,
        payPeriodEnd,
        null,
        null,
        bills,
        annualWithdrawals,
        assetAccounts,
        debtAccounts,
        incomeSummaryItems,
        incomeEvents,
        importantDates);
  }

  public FinancialSnapshot withVersion(long replacementVersion) {
    return new FinancialSnapshot(
        replacementVersion,
        payPeriodStart,
        payPeriodEnd,
        planningSettings,
        projectionRoles,
        bills,
        annualWithdrawals,
        assetAccounts,
        debtAccounts,
        incomeSummaryItems,
        incomeEvents,
        importantDates);
  }

  private static <T> List<T> immutableOrEmpty(List<T> value) {
    return value == null ? List.of() : List.copyOf(value);
  }
}

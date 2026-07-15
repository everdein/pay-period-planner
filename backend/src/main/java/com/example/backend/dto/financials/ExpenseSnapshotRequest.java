package com.example.backend.dto.financials;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;

public record ExpenseSnapshotRequest(
    @NotNull(message = "Snapshot version is required")
        @Positive(message = "Snapshot version must be positive")
        Long version,
    @NotNull(message = "Pay period start date is required") LocalDate payPeriodStart,
    @NotNull(message = "Pay period end date is required") LocalDate payPeriodEnd,
    @Valid FinancialPlanningSettingsRequest planningSettings,
    @Valid FinancialProjectionRolesRequest projectionRoles,
    @NotNull(message = "Bills are required")
        List<@NotNull(message = "Bill record is required") @Valid ExpenseBillSnapshotRequest> bills,
    List<
            @NotNull(message = "Annual withdrawal record is required") @Valid
            AnnualWithdrawalSnapshotRequest>
        annualWithdrawals,
    @NotNull(message = "Asset categories are required")
        List<
                @NotNull(message = "Asset category record is required") @Valid
                AssetCategorySnapshotRequest>
            assetCategories,
    List<@NotNull(message = "Debt account record is required") @Valid DebtAccountSnapshotRequest>
        debtAccounts,
    List<
            @NotNull(message = "Income summary item record is required") @Valid
            IncomeSummaryItemSnapshotRequest>
        incomeSummaryItems,
    @NotNull(message = "Income events are required")
        List<
                @NotNull(message = "Income event record is required") @Valid
                IncomeEventSnapshotRequest>
            incomeEvents,
    @NotNull(message = "Important dates are required")
        List<
                @NotNull(message = "Important date record is required") @Valid
                ImportantDateSnapshotRequest>
            importantDates) {

  public ExpenseSnapshotRequest(
      Long version,
      LocalDate payPeriodStart,
      LocalDate payPeriodEnd,
      FinancialProjectionRolesRequest projectionRoles,
      List<ExpenseBillSnapshotRequest> bills,
      List<AnnualWithdrawalSnapshotRequest> annualWithdrawals,
      List<AssetCategorySnapshotRequest> assetCategories,
      List<DebtAccountSnapshotRequest> debtAccounts,
      List<IncomeSummaryItemSnapshotRequest> incomeSummaryItems,
      List<IncomeEventSnapshotRequest> incomeEvents,
      List<ImportantDateSnapshotRequest> importantDates) {
    this(
        version,
        payPeriodStart,
        payPeriodEnd,
        null,
        projectionRoles,
        bills,
        annualWithdrawals,
        assetCategories,
        debtAccounts,
        incomeSummaryItems,
        incomeEvents,
        importantDates);
  }

  public ExpenseSnapshotRequest(
      Long version,
      LocalDate payPeriodStart,
      LocalDate payPeriodEnd,
      List<ExpenseBillSnapshotRequest> bills,
      List<AnnualWithdrawalSnapshotRequest> annualWithdrawals,
      List<AssetCategorySnapshotRequest> assetCategories,
      List<DebtAccountSnapshotRequest> debtAccounts,
      List<IncomeSummaryItemSnapshotRequest> incomeSummaryItems,
      List<IncomeEventSnapshotRequest> incomeEvents,
      List<ImportantDateSnapshotRequest> importantDates) {
    this(
        version,
        payPeriodStart,
        payPeriodEnd,
        null,
        null,
        bills,
        annualWithdrawals,
        assetCategories,
        debtAccounts,
        incomeSummaryItems,
        incomeEvents,
        importantDates);
  }
}

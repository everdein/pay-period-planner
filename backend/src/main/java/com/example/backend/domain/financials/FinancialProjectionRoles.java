package com.example.backend.domain.financials;

public record FinancialProjectionRoles(
    long rentBillId, long rentReserveAssetAccountId, long primaryPaycheckIncomeSummaryItemId) {

  public static final String RENT_BILL = "rent-bill";
  public static final String RENT_RESERVE_ASSET_ACCOUNT = "rent-reserve-asset-account";
  public static final String PRIMARY_PAYCHECK_INCOME_SUMMARY_ITEM =
      "primary-paycheck-income-summary-item";
}

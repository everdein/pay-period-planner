package com.example.backend.service;

import com.example.backend.domain.financials.AssetAccount;
import com.example.backend.domain.financials.ExpenseBill;
import com.example.backend.domain.financials.FinancialPlanningSettings;
import com.example.backend.domain.financials.FinancialProjectionRoles;
import com.example.backend.domain.financials.FinancialSnapshot;
import com.example.backend.domain.financials.IncomeSummaryItem;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class FinancialSnapshotNormalizer {

  private static final String RENT_WITHDRAWAL_NAME = "Rent";
  private static final String RENT_RESERVE_ACCOUNT_NAME = "Rent Reserve";
  private static final String PRIMARY_PAYCHECK_CATEGORY = "Net Income";
  private static final String PRIMARY_PAYCHECK_INTERVAL = "Bi-Weekly";

  public FinancialSnapshot normalize(FinancialSnapshot snapshot) {
    RecordIdAllocator billIds = allocator(snapshot.bills().stream().mapToLong(ExpenseBill::id));
    RecordIdAllocator assetIds =
        allocator(snapshot.assetAccounts().stream().mapToLong(AssetAccount::id));
    RecordIdAllocator incomeIds =
        allocator(snapshot.incomeSummaryItems().stream().mapToLong(IncomeSummaryItem::id));
    List<ExpenseBill> bills = assignMissingBillIds(snapshot.bills(), billIds);
    List<AssetAccount> assetAccounts = assignMissingAssetIds(snapshot.assetAccounts(), assetIds);
    List<IncomeSummaryItem> incomeItems =
        assignMissingIncomeIds(snapshot.incomeSummaryItems(), incomeIds);
    FinancialProjectionRoles roles = snapshot.projectionRoles();
    FinancialPlanningSettings planningSettings = snapshot.planningSettings();

    if (planningSettings == null) {
      planningSettings = FinancialPlanningSettings.legacyDefaults();
    }

    if (roles == null) {
      LegacyProjectionDefaults defaults =
          addLegacyProjectionDefaults(
              bills, assetAccounts, incomeItems, billIds, assetIds, incomeIds);
      bills = defaults.bills();
      assetAccounts = defaults.assetAccounts();
      incomeItems = defaults.incomeItems();
      roles = defaults.roles();
    }

    validateRole(
        bills.stream().mapToLong(ExpenseBill::id).toArray(),
        roles.rentBillId(),
        "Rent bill projection role must reference one monthly withdrawal");
    validateRole(
        assetAccounts.stream().mapToLong(AssetAccount::id).toArray(),
        roles.rentReserveAssetAccountId(),
        "Rent reserve projection role must reference one asset account");
    validateRole(
        incomeItems.stream().mapToLong(IncomeSummaryItem::id).toArray(),
        roles.primaryPaycheckIncomeSummaryItemId(),
        "Primary paycheck projection role must reference one income summary item");

    return new FinancialSnapshot(
        snapshot.version(),
        snapshot.payPeriodStart(),
        snapshot.payPeriodEnd(),
        planningSettings,
        roles,
        bills,
        snapshot.annualWithdrawals(),
        assetAccounts,
        snapshot.debtAccounts(),
        incomeItems,
        snapshot.incomeEvents(),
        snapshot.importantDates());
  }

  private LegacyProjectionDefaults addLegacyProjectionDefaults(
      List<ExpenseBill> sourceBills,
      List<AssetAccount> sourceAssets,
      List<IncomeSummaryItem> sourceIncomeItems,
      RecordIdAllocator billIds,
      RecordIdAllocator assetIds,
      RecordIdAllocator incomeIds) {
    List<ExpenseBill> bills = new ArrayList<>(sourceBills);
    ExpenseBill rentBill =
        bills.stream().filter(this::isExactRentWithdrawal).findFirst().orElse(null);
    if (rentBill == null) {
      rentBill = bills.stream().filter(this::mentionsRent).findFirst().orElse(null);
    }
    if (rentBill == null) {
      rentBill =
          new ExpenseBill(billIds.next(), RENT_WITHDRAWAL_NAME, 1, BigDecimal.ZERO, "Check", false);
      bills.add(rentBill);
    }

    List<AssetAccount> assetAccounts = new ArrayList<>(sourceAssets);
    AssetAccount rentReserve =
        assetAccounts.stream().filter(this::isExactRentReserveAccount).findFirst().orElse(null);
    if (rentReserve == null) {
      rentReserve =
          assetAccounts.stream()
              .filter(
                  (account) ->
                      account.categoryKey().equals("cash-savings") && mentionsRent(account))
              .findFirst()
              .orElse(null);
    }
    if (rentReserve == null) {
      AssetAccount cashSavings =
          assetAccounts.stream()
              .filter((account) -> account.categoryKey().equals("cash-savings"))
              .findFirst()
              .orElse(null);
      rentReserve =
          new AssetAccount(
              assetIds.next(),
              cashSavings == null ? "cash-savings" : cashSavings.categoryKey(),
              cashSavings == null ? "Cash & Savings" : cashSavings.categoryLabel(),
              RENT_RESERVE_ACCOUNT_NAME,
              "Credit Union",
              BigDecimal.ZERO);
      assetAccounts.add(rentReserve);
    }

    List<IncomeSummaryItem> incomeItems = new ArrayList<>(sourceIncomeItems);
    IncomeSummaryItem primaryPaycheck =
        incomeItems.stream().filter(this::isExactPrimaryPaycheck).findFirst().orElse(null);
    if (primaryPaycheck == null) {
      primaryPaycheck =
          new IncomeSummaryItem(
              incomeIds.next(),
              PRIMARY_PAYCHECK_CATEGORY,
              PRIMARY_PAYCHECK_INTERVAL,
              BigDecimal.ZERO);
      incomeItems.add(primaryPaycheck);
    }

    return new LegacyProjectionDefaults(
        List.copyOf(bills),
        List.copyOf(assetAccounts),
        List.copyOf(incomeItems),
        new FinancialProjectionRoles(rentBill.id(), rentReserve.id(), primaryPaycheck.id()));
  }

  private RecordIdAllocator allocator(java.util.stream.LongStream recordIds) {
    return new RecordIdAllocator(recordIds.filter((id) -> id > 0).max().orElse(0) + 1);
  }

  private List<ExpenseBill> assignMissingBillIds(List<ExpenseBill> bills, RecordIdAllocator ids) {
    return bills.stream().map((bill) -> bill.id() == 0 ? bill.withId(ids.next()) : bill).toList();
  }

  private List<AssetAccount> assignMissingAssetIds(
      List<AssetAccount> accounts, RecordIdAllocator ids) {
    return accounts.stream()
        .map((account) -> account.id() == 0 ? account.withId(ids.next()) : account)
        .toList();
  }

  private List<IncomeSummaryItem> assignMissingIncomeIds(
      List<IncomeSummaryItem> items, RecordIdAllocator ids) {
    return items.stream().map((item) -> item.id() == 0 ? item.withId(ids.next()) : item).toList();
  }

  private void validateRole(long[] recordIds, long roleId, String message) {
    long matches = java.util.Arrays.stream(recordIds).filter((id) -> id == roleId).count();
    if (matches != 1) {
      throw new FinancialRequestException(message);
    }
  }

  private boolean isExactRentWithdrawal(ExpenseBill bill) {
    return bill.bill().trim().equalsIgnoreCase(RENT_WITHDRAWAL_NAME);
  }

  private boolean mentionsRent(ExpenseBill bill) {
    return bill.bill().toLowerCase().contains("rent");
  }

  private boolean isExactRentReserveAccount(AssetAccount account) {
    return account.account().trim().equalsIgnoreCase(RENT_RESERVE_ACCOUNT_NAME);
  }

  private boolean mentionsRent(AssetAccount account) {
    return account.account().toLowerCase().contains("rent");
  }

  private boolean isExactPrimaryPaycheck(IncomeSummaryItem item) {
    return item.category().trim().equalsIgnoreCase(PRIMARY_PAYCHECK_CATEGORY)
        && item.interval().trim().equalsIgnoreCase(PRIMARY_PAYCHECK_INTERVAL);
  }

  private record LegacyProjectionDefaults(
      List<ExpenseBill> bills,
      List<AssetAccount> assetAccounts,
      List<IncomeSummaryItem> incomeItems,
      FinancialProjectionRoles roles) {}

  private static final class RecordIdAllocator {
    private long next;

    private RecordIdAllocator(long next) {
      this.next = next;
    }

    private long next() {
      return next++;
    }
  }
}

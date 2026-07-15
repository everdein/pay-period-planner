package com.example.backend.repository;

import com.example.backend.domain.financials.AnnualWithdrawal;
import com.example.backend.domain.financials.AssetAccount;
import com.example.backend.domain.financials.DebtAccount;
import com.example.backend.domain.financials.ExpenseBill;
import com.example.backend.domain.financials.FinancialAuditEvent;
import com.example.backend.domain.financials.FinancialPlanningSettings;
import com.example.backend.domain.financials.FinancialProjectionRoles;
import com.example.backend.domain.financials.FinancialProjectionSummary;
import com.example.backend.domain.financials.FinancialSnapshot;
import com.example.backend.domain.financials.ImportantDate;
import com.example.backend.domain.financials.IncomeEvent;
import com.example.backend.domain.financials.IncomeSummaryItem;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.web.context.annotation.RequestScope;

@Repository
@RequestScope
public class FinancialsRepository {

  private final AtomicLong nextId = new AtomicLong(1);
  private final AtomicLong nextAnnualWithdrawalId = new AtomicLong(1);
  private final AtomicLong nextAssetId = new AtomicLong(1);
  private final AtomicLong nextDebtAccountId = new AtomicLong(1);
  private final AtomicLong nextIncomeSummaryItemId = new AtomicLong(1);
  private final AtomicLong nextIncomeEventId = new AtomicLong(1);
  private final AtomicLong nextImportantDateId = new AtomicLong(1);
  private final FinancialsSnapshotStore snapshotStore;
  private final Clock clock;
  private final List<ExpenseBill> bills = new ArrayList<>();
  private final List<AnnualWithdrawal> annualWithdrawals = new ArrayList<>();
  private final List<AssetAccount> assetAccounts = new ArrayList<>();
  private final List<DebtAccount> debtAccounts = new ArrayList<>();
  private final List<IncomeSummaryItem> incomeSummaryItems = new ArrayList<>();
  private final List<IncomeEvent> incomeEvents = new ArrayList<>();
  private final List<ImportantDate> importantDates = new ArrayList<>();
  private LocalDate payPeriodStart = LocalDate.now().withDayOfMonth(1);
  private LocalDate payPeriodEnd = LocalDate.now().withDayOfMonth(15);
  private FinancialPlanningSettings planningSettings;
  private FinancialProjectionRoles projectionRoles;
  private long version = 1;
  private boolean loaded;

  @Autowired
  public FinancialsRepository(FinancialsSnapshotStore snapshotStore) {
    this(snapshotStore, Clock.systemUTC());
  }

  FinancialsRepository(FinancialsSnapshotStore snapshotStore, Clock clock) {
    this.snapshotStore = snapshotStore;
    this.clock = clock;
  }

  public synchronized List<FinancialAuditEvent> auditEvents(int limit) {
    return snapshotStore.loadAuditHistory(limit);
  }

  public synchronized FinancialSnapshot currentSnapshot() {
    ensureLoaded();
    return new FinancialSnapshot(
        version,
        payPeriodStart,
        payPeriodEnd,
        planningSettings,
        projectionRoles,
        List.copyOf(bills),
        List.copyOf(annualWithdrawals),
        List.copyOf(assetAccounts),
        List.copyOf(debtAccounts),
        List.copyOf(incomeSummaryItems),
        List.copyOf(incomeEvents),
        List.copyOf(importantDates));
  }

  public synchronized void replaceSnapshot(
      long expectedVersion, FinancialSnapshot replacementSnapshot) {
    ensureLoaded();
    if (expectedVersion != version) {
      throw new SnapshotVersionConflictException(expectedVersion, version);
    }

    Map<Long, Long> billIds = new HashMap<>();
    bills.clear();
    for (ExpenseBill bill : replacementSnapshot.bills()) {
      long id = bill.id() > 0 ? bill.id() : nextId.getAndIncrement();
      billIds.put(bill.id(), id);
      bills.add(bill.withId(id));
      nextId.updateAndGet((current) -> Math.max(current, id + 1));
    }

    annualWithdrawals.clear();
    for (AnnualWithdrawal annualWithdrawal : replacementSnapshot.annualWithdrawals()) {
      long id =
          annualWithdrawal.id() > 0
              ? annualWithdrawal.id()
              : nextAnnualWithdrawalId.getAndIncrement();
      annualWithdrawals.add(annualWithdrawal.withId(id));
      nextAnnualWithdrawalId.updateAndGet((current) -> Math.max(current, id + 1));
    }

    Map<Long, Long> assetIds = new HashMap<>();
    assetAccounts.clear();
    for (AssetAccount account : replacementSnapshot.assetAccounts()) {
      long id = account.id() > 0 ? account.id() : nextAssetId.getAndIncrement();
      assetIds.put(account.id(), id);
      assetAccounts.add(account.withId(id));
      nextAssetId.updateAndGet((current) -> Math.max(current, id + 1));
    }

    debtAccounts.clear();
    for (DebtAccount account : replacementSnapshot.debtAccounts()) {
      long id = account.id() > 0 ? account.id() : nextDebtAccountId.getAndIncrement();
      debtAccounts.add(account.withId(id));
      nextDebtAccountId.updateAndGet((current) -> Math.max(current, id + 1));
    }

    Map<Long, Long> incomeSummaryItemIds = new HashMap<>();
    incomeSummaryItems.clear();
    for (IncomeSummaryItem item : replacementSnapshot.incomeSummaryItems()) {
      long id = item.id() > 0 ? item.id() : nextIncomeSummaryItemId.getAndIncrement();
      incomeSummaryItemIds.put(item.id(), id);
      incomeSummaryItems.add(item.withId(id));
      nextIncomeSummaryItemId.updateAndGet((current) -> Math.max(current, id + 1));
    }

    incomeEvents.clear();
    for (IncomeEvent event : replacementSnapshot.incomeEvents()) {
      long id = event.id() > 0 ? event.id() : nextIncomeEventId.getAndIncrement();
      incomeEvents.add(event.withId(id));
      nextIncomeEventId.updateAndGet((current) -> Math.max(current, id + 1));
    }

    importantDates.clear();
    for (ImportantDate importantDate : replacementSnapshot.importantDates()) {
      long id = importantDate.id() > 0 ? importantDate.id() : nextImportantDateId.getAndIncrement();
      importantDates.add(importantDate.withId(id));
      nextImportantDateId.updateAndGet((current) -> Math.max(current, id + 1));
    }

    payPeriodStart = replacementSnapshot.payPeriodStart();
    payPeriodEnd = replacementSnapshot.payPeriodEnd();
    planningSettings = replacementSnapshot.planningSettings();
    projectionRoles =
        remapProjectionRoles(
            replacementSnapshot.projectionRoles(), billIds, assetIds, incomeSummaryItemIds);
    persist("REPLACE", "snapshot", null, "Replaced full financial snapshot");
  }

  private void ensureLoaded() {
    if (loaded) {
      return;
    }

    FinancialSnapshot snapshot = snapshotStore.loadCurrentSnapshot();
    version = snapshot.version();
    payPeriodStart = snapshot.payPeriodStart();
    payPeriodEnd = snapshot.payPeriodEnd();
    planningSettings = snapshot.planningSettings();
    projectionRoles = snapshot.projectionRoles();
    bills.clear();
    bills.addAll(snapshot.bills());
    annualWithdrawals.clear();
    annualWithdrawals.addAll(snapshot.annualWithdrawals());
    assetAccounts.clear();
    assetAccounts.addAll(snapshot.assetAccounts());
    debtAccounts.clear();
    debtAccounts.addAll(snapshot.debtAccounts());
    incomeSummaryItems.clear();
    incomeSummaryItems.addAll(snapshot.incomeSummaryItems());
    incomeEvents.clear();
    incomeEvents.addAll(snapshot.incomeEvents());
    importantDates.clear();
    importantDates.addAll(snapshot.importantDates());
    resetNextIds();
    loaded = true;
  }

  private FinancialProjectionRoles remapProjectionRoles(
      FinancialProjectionRoles roles,
      Map<Long, Long> billIds,
      Map<Long, Long> assetIds,
      Map<Long, Long> incomeSummaryItemIds) {
    if (roles == null) {
      return null;
    }
    return new FinancialProjectionRoles(
        billIds.getOrDefault(roles.rentBillId(), roles.rentBillId()),
        assetIds.getOrDefault(roles.rentReserveAssetAccountId(), roles.rentReserveAssetAccountId()),
        incomeSummaryItemIds.getOrDefault(
            roles.primaryPaycheckIncomeSummaryItemId(),
            roles.primaryPaycheckIncomeSummaryItemId()));
  }

  private void persist(String action, String resourceType, Long resourceId, String summary) {
    long versionBefore = version;
    version += 1;
    FinancialAuditEvent auditEvent =
        new FinancialAuditEvent(
            0,
            Instant.now(clock),
            action,
            resourceType,
            resourceId,
            versionBefore,
            version,
            summary,
            projectionSummary());
    snapshotStore.replaceSnapshot(versionBefore, currentSnapshot(), auditEvent);
  }

  private void resetNextIds() {
    long maxBillId = bills.stream().mapToLong(ExpenseBill::id).max().orElse(0);
    long maxAnnualWithdrawalId =
        annualWithdrawals.stream().mapToLong(AnnualWithdrawal::id).max().orElse(0);
    long maxAssetId = assetAccounts.stream().mapToLong(AssetAccount::id).max().orElse(0);
    long maxDebtAccountId = debtAccounts.stream().mapToLong(DebtAccount::id).max().orElse(0);
    long maxIncomeSummaryItemId =
        incomeSummaryItems.stream().mapToLong(IncomeSummaryItem::id).max().orElse(0);
    long maxIncomeEventId = incomeEvents.stream().mapToLong(IncomeEvent::id).max().orElse(0);
    long maxImportantDateId = importantDates.stream().mapToLong(ImportantDate::id).max().orElse(0);
    nextId.set(maxBillId + 1);
    nextAnnualWithdrawalId.set(maxAnnualWithdrawalId + 1);
    nextAssetId.set(maxAssetId + 1);
    nextDebtAccountId.set(maxDebtAccountId + 1);
    nextIncomeSummaryItemId.set(maxIncomeSummaryItemId + 1);
    nextIncomeEventId.set(maxIncomeEventId + 1);
    nextImportantDateId.set(maxImportantDateId + 1);
  }

  private FinancialProjectionSummary projectionSummary() {
    BigDecimal totalMonthlyExpenses = sum(bills.stream().map(ExpenseBill::amount).toList());
    BigDecimal totalAnnualWithdrawals =
        sum(annualWithdrawals.stream().map(AnnualWithdrawal::amount).toList());
    BigDecimal totalTrackedAssets = sum(assetAccounts.stream().map(AssetAccount::amount).toList());
    BigDecimal totalDebt = sum(debtAccounts.stream().map(DebtAccount::amount).toList());

    return new FinancialProjectionSummary(
        payPeriodStart,
        payPeriodEnd,
        bills.size(),
        annualWithdrawals.size(),
        assetAccounts.size(),
        debtAccounts.size(),
        incomeSummaryItems.size(),
        incomeEvents.size(),
        importantDates.size(),
        totalMonthlyExpenses,
        totalAnnualWithdrawals,
        totalTrackedAssets,
        totalDebt,
        totalTrackedAssets.subtract(totalDebt));
  }

  private BigDecimal sum(List<BigDecimal> values) {
    return values.stream().filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}

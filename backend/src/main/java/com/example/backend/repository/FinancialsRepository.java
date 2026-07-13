package com.example.backend.repository;

import com.example.backend.domain.financials.AnnualWithdrawal;
import com.example.backend.domain.financials.AssetAccount;
import com.example.backend.domain.financials.DebtAccount;
import com.example.backend.domain.financials.ExpenseBill;
import com.example.backend.domain.financials.FinancialAuditEvent;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class FinancialsRepository {

  private final AtomicLong nextId = new AtomicLong(1);
  private final AtomicLong nextAnnualWithdrawalId = new AtomicLong(1);
  private final AtomicLong nextAssetId = new AtomicLong(1);
  private final AtomicLong nextDebtAccountId = new AtomicLong(1);
  private final AtomicLong nextIncomeSummaryItemId = new AtomicLong(1);
  private final AtomicLong nextIncomeEventId = new AtomicLong(1);
  private final AtomicLong nextImportantDateId = new AtomicLong(1);
  private final AtomicLong nextAuditEventId = new AtomicLong(1);
  private final FinancialsSnapshotStore snapshotStore;
  private final Clock clock;
  private final List<ExpenseBill> bills = new ArrayList<>();
  private final List<AnnualWithdrawal> annualWithdrawals = new ArrayList<>();
  private final List<AssetAccount> assetAccounts = new ArrayList<>();
  private final List<DebtAccount> debtAccounts = new ArrayList<>();
  private final List<IncomeSummaryItem> incomeSummaryItems = new ArrayList<>();
  private final List<IncomeEvent> incomeEvents = new ArrayList<>();
  private final List<ImportantDate> importantDates = new ArrayList<>();
  private final List<FinancialAuditEvent> auditEvents = new ArrayList<>();
  private LocalDate payPeriodStart = LocalDate.now().withDayOfMonth(1);
  private LocalDate payPeriodEnd = LocalDate.now().withDayOfMonth(15);
  private long version = 1;

  @Autowired
  public FinancialsRepository(FinancialsSnapshotStore snapshotStore) {
    this(snapshotStore, Clock.systemUTC());
  }

  FinancialsRepository(FinancialsSnapshotStore snapshotStore, Clock clock) {
    this.snapshotStore = snapshotStore;
    this.clock = clock;
    load();
  }

  public synchronized List<ExpenseBill> findAllBills() {
    return bills.stream().sorted(Comparator.comparingInt(ExpenseBill::dueDay)).toList();
  }

  public synchronized List<AnnualWithdrawal> findAllAnnualWithdrawals() {
    return annualWithdrawals.stream()
        .sorted(
            Comparator.comparingInt(AnnualWithdrawal::month)
                .thenComparingInt(AnnualWithdrawal::day))
        .toList();
  }

  public synchronized List<AssetAccount> findAllAssetAccounts() {
    return List.copyOf(assetAccounts);
  }

  public synchronized List<DebtAccount> findAllDebtAccounts() {
    return debtAccounts.stream().sorted(Comparator.comparing(DebtAccount::account)).toList();
  }

  public synchronized List<IncomeSummaryItem> findAllIncomeSummaryItems() {
    return incomeSummaryItems.stream()
        .sorted(
            Comparator.comparing(IncomeSummaryItem::category)
                .thenComparing(IncomeSummaryItem::interval))
        .toList();
  }

  public synchronized List<IncomeEvent> findAllIncomeEvents() {
    return incomeEvents.stream().sorted(Comparator.comparing(IncomeEvent::date)).toList();
  }

  public synchronized List<ImportantDate> findAllImportantDates() {
    return importantDates.stream().sorted(Comparator.comparing(ImportantDate::date)).toList();
  }

  public synchronized long version() {
    return version;
  }

  public synchronized List<FinancialAuditEvent> auditEvents(int limit) {
    List<FinancialAuditEvent> newestFirst = new ArrayList<>(auditEvents);
    Collections.reverse(newestFirst);
    return newestFirst.stream().limit(Math.max(0, limit)).toList();
  }

  public synchronized FinancialSnapshot currentSnapshot() {
    return new FinancialSnapshot(
        version,
        payPeriodStart,
        payPeriodEnd,
        List.copyOf(bills),
        List.copyOf(annualWithdrawals),
        List.copyOf(assetAccounts),
        List.copyOf(debtAccounts),
        List.copyOf(incomeSummaryItems),
        List.copyOf(incomeEvents),
        List.copyOf(importantDates));
  }

  public synchronized ExpenseBill addBill(ExpenseBill bill) {
    ExpenseBill created = bill.withId(nextId.getAndIncrement());
    bills.add(created);
    persist("CREATE", "monthly-bill", created.id(), "Created monthly bill");
    return created;
  }

  public synchronized Optional<ExpenseBill> updateBill(long id, ExpenseBill bill) {
    for (int index = 0; index < bills.size(); index++) {
      if (bills.get(index).id() == id) {
        ExpenseBill updated = bill.withId(id);
        bills.set(index, updated);
        persist("UPDATE", "monthly-bill", id, "Updated monthly bill");
        return Optional.of(updated);
      }
    }

    return Optional.empty();
  }

  public synchronized boolean deleteBill(long id) {
    boolean removed = bills.removeIf((bill) -> bill.id() == id);
    if (removed) {
      persist("DELETE", "monthly-bill", id, "Deleted monthly bill");
    }
    return removed;
  }

  public synchronized AnnualWithdrawal addAnnualWithdrawal(AnnualWithdrawal withdrawal) {
    AnnualWithdrawal created = withdrawal.withId(nextAnnualWithdrawalId.getAndIncrement());
    annualWithdrawals.add(created);
    persist("CREATE", "annual-withdrawal", created.id(), "Created annual withdrawal");
    return created;
  }

  public synchronized Optional<AnnualWithdrawal> updateAnnualWithdrawal(
      long id, AnnualWithdrawal withdrawal) {
    for (int index = 0; index < annualWithdrawals.size(); index++) {
      if (annualWithdrawals.get(index).id() == id) {
        AnnualWithdrawal updated = withdrawal.withId(id);
        annualWithdrawals.set(index, updated);
        persist("UPDATE", "annual-withdrawal", id, "Updated annual withdrawal");
        return Optional.of(updated);
      }
    }

    return Optional.empty();
  }

  public synchronized boolean deleteAnnualWithdrawal(long id) {
    boolean removed = annualWithdrawals.removeIf((withdrawal) -> withdrawal.id() == id);
    if (removed) {
      persist("DELETE", "annual-withdrawal", id, "Deleted annual withdrawal");
    }
    return removed;
  }

  public synchronized AssetAccount addAssetAccount(AssetAccount account) {
    AssetAccount created = account.withId(nextAssetId.getAndIncrement());
    assetAccounts.add(created);
    persist("CREATE", "asset-account", created.id(), "Created asset account");
    return created;
  }

  public synchronized Optional<AssetAccount> updateAssetAccount(long id, AssetAccount account) {
    for (int index = 0; index < assetAccounts.size(); index++) {
      if (assetAccounts.get(index).id() == id) {
        AssetAccount updated = account.withId(id);
        assetAccounts.set(index, updated);
        persist("UPDATE", "asset-account", id, "Updated asset account");
        return Optional.of(updated);
      }
    }

    return Optional.empty();
  }

  public synchronized boolean deleteAssetAccount(long id) {
    boolean removed = assetAccounts.removeIf((account) -> account.id() == id);
    if (removed) {
      persist("DELETE", "asset-account", id, "Deleted asset account");
    }
    return removed;
  }

  public synchronized DebtAccount addDebtAccount(DebtAccount account) {
    DebtAccount created = account.withId(nextDebtAccountId.getAndIncrement());
    debtAccounts.add(created);
    persist("CREATE", "debt-account", created.id(), "Created debt account");
    return created;
  }

  public synchronized Optional<DebtAccount> updateDebtAccount(long id, DebtAccount account) {
    for (int index = 0; index < debtAccounts.size(); index++) {
      if (debtAccounts.get(index).id() == id) {
        DebtAccount updated = account.withId(id);
        debtAccounts.set(index, updated);
        persist("UPDATE", "debt-account", id, "Updated debt account");
        return Optional.of(updated);
      }
    }

    return Optional.empty();
  }

  public synchronized boolean deleteDebtAccount(long id) {
    boolean removed = debtAccounts.removeIf((account) -> account.id() == id);
    if (removed) {
      persist("DELETE", "debt-account", id, "Deleted debt account");
    }
    return removed;
  }

  public synchronized IncomeSummaryItem addIncomeSummaryItem(IncomeSummaryItem item) {
    IncomeSummaryItem created = item.withId(nextIncomeSummaryItemId.getAndIncrement());
    incomeSummaryItems.add(created);
    persist("CREATE", "income-summary-item", created.id(), "Created income summary item");
    return created;
  }

  public synchronized Optional<IncomeSummaryItem> updateIncomeSummaryItem(
      long id, IncomeSummaryItem item) {
    for (int index = 0; index < incomeSummaryItems.size(); index++) {
      if (incomeSummaryItems.get(index).id() == id) {
        IncomeSummaryItem updated = item.withId(id);
        incomeSummaryItems.set(index, updated);
        persist("UPDATE", "income-summary-item", id, "Updated income summary item");
        return Optional.of(updated);
      }
    }

    return Optional.empty();
  }

  public synchronized boolean deleteIncomeSummaryItem(long id) {
    boolean removed = incomeSummaryItems.removeIf((item) -> item.id() == id);
    if (removed) {
      persist("DELETE", "income-summary-item", id, "Deleted income summary item");
    }
    return removed;
  }

  public synchronized IncomeEvent addIncomeEvent(IncomeEvent event) {
    IncomeEvent created = event.withId(nextIncomeEventId.getAndIncrement());
    incomeEvents.add(created);
    persist("CREATE", "income-event", created.id(), "Created income event");
    return created;
  }

  public synchronized Optional<IncomeEvent> updateIncomeEvent(long id, IncomeEvent event) {
    for (int index = 0; index < incomeEvents.size(); index++) {
      if (incomeEvents.get(index).id() == id) {
        IncomeEvent updated = event.withId(id);
        incomeEvents.set(index, updated);
        persist("UPDATE", "income-event", id, "Updated income event");
        return Optional.of(updated);
      }
    }

    return Optional.empty();
  }

  public synchronized boolean deleteIncomeEvent(long id) {
    boolean removed = incomeEvents.removeIf((event) -> event.id() == id);
    if (removed) {
      persist("DELETE", "income-event", id, "Deleted income event");
    }
    return removed;
  }

  public synchronized ImportantDate addImportantDate(ImportantDate importantDate) {
    ImportantDate created = importantDate.withId(nextImportantDateId.getAndIncrement());
    importantDates.add(created);
    persist("CREATE", "important-date", created.id(), "Created important date");
    return created;
  }

  public synchronized Optional<ImportantDate> updateImportantDate(
      long id, ImportantDate importantDate) {
    for (int index = 0; index < importantDates.size(); index++) {
      if (importantDates.get(index).id() == id) {
        ImportantDate updated = importantDate.withId(id);
        importantDates.set(index, updated);
        persist("UPDATE", "important-date", id, "Updated important date");
        return Optional.of(updated);
      }
    }

    return Optional.empty();
  }

  public synchronized boolean deleteImportantDate(long id) {
    boolean removed = importantDates.removeIf((importantDate) -> importantDate.id() == id);
    if (removed) {
      persist("DELETE", "important-date", id, "Deleted important date");
    }
    return removed;
  }

  public synchronized void replaceSnapshot(
      long expectedVersion, FinancialSnapshot replacementSnapshot) {
    if (expectedVersion != version) {
      throw new SnapshotVersionConflictException(expectedVersion, version);
    }

    bills.clear();
    for (ExpenseBill bill : replacementSnapshot.bills()) {
      long id = bill.id() > 0 ? bill.id() : nextId.getAndIncrement();
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

    assetAccounts.clear();
    for (AssetAccount account : replacementSnapshot.assetAccounts()) {
      long id = account.id() > 0 ? account.id() : nextAssetId.getAndIncrement();
      assetAccounts.add(account.withId(id));
      nextAssetId.updateAndGet((current) -> Math.max(current, id + 1));
    }

    debtAccounts.clear();
    for (DebtAccount account : replacementSnapshot.debtAccounts()) {
      long id = account.id() > 0 ? account.id() : nextDebtAccountId.getAndIncrement();
      debtAccounts.add(account.withId(id));
      nextDebtAccountId.updateAndGet((current) -> Math.max(current, id + 1));
    }

    incomeSummaryItems.clear();
    for (IncomeSummaryItem item : replacementSnapshot.incomeSummaryItems()) {
      long id = item.id() > 0 ? item.id() : nextIncomeSummaryItemId.getAndIncrement();
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
    persist("REPLACE", "snapshot", null, "Replaced full financial snapshot");
  }

  public synchronized LocalDate payPeriodStart() {
    return payPeriodStart;
  }

  public synchronized LocalDate payPeriodEnd() {
    return payPeriodEnd;
  }

  public synchronized void updatePayPeriod(LocalDate startDate, LocalDate endDate) {
    payPeriodStart = startDate;
    payPeriodEnd = endDate;
    persist("UPDATE", "pay-period", null, "Updated pay period anchors");
  }

  private void load() {
    FinancialsData data = snapshotStore.load();
    FinancialSnapshot snapshot = data.toSnapshot();
    version = snapshot.version();
    payPeriodStart = snapshot.payPeriodStart();
    payPeriodEnd = snapshot.payPeriodEnd();
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
    auditEvents.clear();
    auditEvents.addAll(data.auditEvents());
    resetNextIds();
  }

  private void persist(String action, String resourceType, Long resourceId, String summary) {
    long versionBefore = version;
    version += 1;
    auditEvents.add(
        new FinancialAuditEvent(
            nextAuditEventId.getAndIncrement(),
            Instant.now(clock),
            action,
            resourceType,
            resourceId,
            versionBefore,
            version,
            summary,
            projectionSummary()));
    snapshotStore.save(FinancialsData.fromSnapshot(currentSnapshot(), auditEvents));
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
    long maxAuditEventId = auditEvents.stream().mapToLong(FinancialAuditEvent::id).max().orElse(0);
    nextId.set(maxBillId + 1);
    nextAnnualWithdrawalId.set(maxAnnualWithdrawalId + 1);
    nextAssetId.set(maxAssetId + 1);
    nextDebtAccountId.set(maxDebtAccountId + 1);
    nextIncomeSummaryItemId.set(maxIncomeSummaryItemId + 1);
    nextIncomeEventId.set(maxIncomeEventId + 1);
    nextImportantDateId.set(maxImportantDateId + 1);
    nextAuditEventId.set(maxAuditEventId + 1);
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

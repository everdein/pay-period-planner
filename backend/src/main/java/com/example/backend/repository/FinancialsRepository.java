package com.example.backend.repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
public class FinancialsRepository {

  private final AtomicLong nextId = new AtomicLong(1);
  private final AtomicLong nextAnnualWithdrawalId = new AtomicLong(1);
  private final AtomicLong nextAssetId = new AtomicLong(1);
  private final AtomicLong nextDebtAccountId = new AtomicLong(1);
  private final AtomicLong nextIncomeSummaryItemId = new AtomicLong(1);
  private final AtomicLong nextIncomeEventId = new AtomicLong(1);
  private final AtomicLong nextImportantDateId = new AtomicLong(1);
  private final ObjectMapper objectMapper;
  private final Path dataPath;
  private final Path examplePath;
  private final List<ExpenseBill> bills = new ArrayList<>();
  private final List<AnnualWithdrawal> annualWithdrawals = new ArrayList<>();
  private final List<AssetAccount> assetAccounts = new ArrayList<>();
  private final List<DebtAccount> debtAccounts = new ArrayList<>();
  private final List<IncomeSummaryItem> incomeSummaryItems = new ArrayList<>();
  private final List<IncomeEvent> incomeEvents = new ArrayList<>();
  private final List<ImportantDate> importantDates = new ArrayList<>();
  private LocalDate payPeriodStart = LocalDate.now().withDayOfMonth(1);
  private LocalDate payPeriodEnd = LocalDate.now().withDayOfMonth(15);

  public FinancialsRepository(
      ObjectMapper objectMapper,
      @Value("${financials.data.path:data/financials.local.json}") Path dataPath,
      @Value("${financials.example-data.path:data/financials.example.json}") Path examplePath) {
    this.objectMapper = objectMapper;
    this.dataPath = dataPath;
    this.examplePath = examplePath;
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

  public synchronized ExpenseBill addBill(ExpenseBill bill) {
    ExpenseBill created = bill.withId(nextId.getAndIncrement());
    bills.add(created);
    persist();
    return created;
  }

  public synchronized Optional<ExpenseBill> updateBill(long id, ExpenseBill bill) {
    for (int index = 0; index < bills.size(); index++) {
      if (bills.get(index).id() == id) {
        ExpenseBill updated = bill.withId(id);
        bills.set(index, updated);
        persist();
        return Optional.of(updated);
      }
    }

    return Optional.empty();
  }

  public synchronized boolean deleteBill(long id) {
    boolean removed = bills.removeIf((bill) -> bill.id() == id);
    if (removed) {
      persist();
    }
    return removed;
  }

  public synchronized void replaceSnapshot(
      LocalDate startDate,
      LocalDate endDate,
      List<ExpenseBill> replacementBills,
      List<AnnualWithdrawal> replacementAnnualWithdrawals,
      List<AssetAccount> replacementAssetAccounts,
      List<DebtAccount> replacementDebtAccounts,
      List<IncomeSummaryItem> replacementIncomeSummaryItems,
      List<IncomeEvent> replacementIncomeEvents,
      List<ImportantDate> replacementImportantDates) {
    bills.clear();
    for (ExpenseBill bill : replacementBills) {
      long id = bill.id() > 0 ? bill.id() : nextId.getAndIncrement();
      bills.add(bill.withId(id));
      nextId.updateAndGet((current) -> Math.max(current, id + 1));
    }

    annualWithdrawals.clear();
    for (AnnualWithdrawal annualWithdrawal : replacementAnnualWithdrawals) {
      long id =
          annualWithdrawal.id() > 0
              ? annualWithdrawal.id()
              : nextAnnualWithdrawalId.getAndIncrement();
      annualWithdrawals.add(annualWithdrawal.withId(id));
      nextAnnualWithdrawalId.updateAndGet((current) -> Math.max(current, id + 1));
    }

    assetAccounts.clear();
    for (AssetAccount account : replacementAssetAccounts) {
      long id = account.id() > 0 ? account.id() : nextAssetId.getAndIncrement();
      assetAccounts.add(account.withId(id));
      nextAssetId.updateAndGet((current) -> Math.max(current, id + 1));
    }

    debtAccounts.clear();
    for (DebtAccount account : replacementDebtAccounts) {
      long id = account.id() > 0 ? account.id() : nextDebtAccountId.getAndIncrement();
      debtAccounts.add(account.withId(id));
      nextDebtAccountId.updateAndGet((current) -> Math.max(current, id + 1));
    }

    incomeSummaryItems.clear();
    for (IncomeSummaryItem item : replacementIncomeSummaryItems) {
      long id = item.id() > 0 ? item.id() : nextIncomeSummaryItemId.getAndIncrement();
      incomeSummaryItems.add(item.withId(id));
      nextIncomeSummaryItemId.updateAndGet((current) -> Math.max(current, id + 1));
    }

    incomeEvents.clear();
    for (IncomeEvent event : replacementIncomeEvents) {
      long id = event.id() > 0 ? event.id() : nextIncomeEventId.getAndIncrement();
      incomeEvents.add(event.withId(id));
      nextIncomeEventId.updateAndGet((current) -> Math.max(current, id + 1));
    }

    importantDates.clear();
    for (ImportantDate importantDate : replacementImportantDates) {
      long id = importantDate.id() > 0 ? importantDate.id() : nextImportantDateId.getAndIncrement();
      importantDates.add(importantDate.withId(id));
      nextImportantDateId.updateAndGet((current) -> Math.max(current, id + 1));
    }

    payPeriodStart = startDate;
    payPeriodEnd = endDate;
    persist();
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
    persist();
  }

  private void load() {
    try {
      ensureDataFile();
      FinancialsData data = objectMapper.readValue(dataPath.toFile(), FinancialsData.class);
      payPeriodStart = data.payPeriodStart();
      payPeriodEnd = data.payPeriodEnd();
      bills.clear();
      bills.addAll(nullSafe(data.bills()));
      annualWithdrawals.clear();
      annualWithdrawals.addAll(nullSafe(data.annualWithdrawals()));
      assetAccounts.clear();
      assetAccounts.addAll(nullSafe(data.assetAccounts()));
      debtAccounts.clear();
      debtAccounts.addAll(nullSafe(data.debtAccounts()));
      incomeSummaryItems.clear();
      incomeSummaryItems.addAll(nullSafe(data.incomeSummaryItems()));
      incomeEvents.clear();
      incomeEvents.addAll(nullSafe(data.incomeEvents()));
      importantDates.clear();
      importantDates.addAll(nullSafe(data.importantDates()));
      resetNextIds();
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to load financial data from " + dataPath, exception);
    }
  }

  private void ensureDataFile() throws IOException {
    Path parent = dataPath.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }

    if (Files.exists(dataPath)) {
      return;
    }

    if (Files.exists(examplePath)) {
      Files.copy(examplePath, dataPath);
      return;
    }

    persist();
  }

  private void persist() {
    try {
      Path parent = dataPath.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      objectMapper
          .writerWithDefaultPrettyPrinter()
          .writeValue(
              dataPath.toFile(),
              new FinancialsData(
                  payPeriodStart,
                  payPeriodEnd,
                  bills,
                  annualWithdrawals,
                  assetAccounts,
                  debtAccounts,
                  incomeSummaryItems,
                  incomeEvents,
                  importantDates));
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to save financial data to " + dataPath, exception);
    }
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

  private <T> List<T> nullSafe(List<T> value) {
    return value == null ? List.of() : value;
  }

  public record FinancialsData(
      LocalDate payPeriodStart,
      LocalDate payPeriodEnd,
      List<ExpenseBill> bills,
      List<AnnualWithdrawal> annualWithdrawals,
      List<AssetAccount> assetAccounts,
      List<DebtAccount> debtAccounts,
      List<IncomeSummaryItem> incomeSummaryItems,
      List<IncomeEvent> incomeEvents,
      List<ImportantDate> importantDates) {}
}

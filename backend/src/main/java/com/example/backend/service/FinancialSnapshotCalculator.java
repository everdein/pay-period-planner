package com.example.backend.service;

import com.example.backend.domain.financials.AnnualWithdrawal;
import com.example.backend.domain.financials.AssetAccount;
import com.example.backend.domain.financials.DebtAccount;
import com.example.backend.domain.financials.ExpenseBill;
import com.example.backend.domain.financials.FinancialSnapshot;
import com.example.backend.domain.financials.ImportantDate;
import com.example.backend.domain.financials.IncomeEvent;
import com.example.backend.domain.financials.IncomeSummaryItem;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class FinancialSnapshotCalculator {

  private static final List<AssetCategoryDefinition> STANDARD_ASSET_CATEGORIES =
      List.of(
          new AssetCategoryDefinition("retirement", "Retirement"),
          new AssetCategoryDefinition("investments", "Investments"),
          new AssetCategoryDefinition("cash-savings", "Cash & Savings"),
          new AssetCategoryDefinition("insurance-benefits", "Insurance / Benefits"));
  private final FinancialSnapshotNormalizer normalizer;

  public FinancialSnapshotCalculator(FinancialSnapshotNormalizer normalizer) {
    this.normalizer = normalizer;
  }

  FinancialSnapshotCalculation calculate(FinancialSnapshot snapshot, Clock clock) {
    snapshot = normalizer.normalize(snapshot);
    LocalDate currentDate =
        PayPeriodDatePolicy.currentDate(clock, snapshot.planningSettings().zoneId());
    PayPeriodDatePolicy.PayPeriod payPeriod =
        PayPeriodDatePolicy.currentPayPeriod(
            snapshot.payPeriodStart(), snapshot.payPeriodEnd(), currentDate);

    List<FinancialSnapshotCalculation.Bill> bills = calculatedBills(snapshot.bills(), payPeriod);
    List<FinancialSnapshotCalculation.Annual> annualWithdrawals =
        calculatedAnnualWithdrawals(snapshot.annualWithdrawals(), payPeriod);
    List<FinancialSnapshotCalculation.AssetCategory> assetCategories =
        calculatedAssetCategories(snapshot.assetAccounts());
    List<DebtAccount> debtAccounts =
        snapshot.debtAccounts().stream()
            .sorted(Comparator.comparing(DebtAccount::account))
            .toList();
    List<IncomeSummaryItem> incomeSummaryItems =
        snapshot.incomeSummaryItems().stream()
            .sorted(
                Comparator.comparing(IncomeSummaryItem::category)
                    .thenComparing(IncomeSummaryItem::interval))
            .toList();
    List<FinancialSnapshotCalculation.Income> incomeEvents =
        calculatedIncomeEvents(snapshot.incomeEvents());
    List<ImportantDate> importantDates =
        snapshot.importantDates().stream()
            .sorted(Comparator.comparing(ImportantDate::date))
            .toList();

    BigDecimal totalMonthlyExpenses =
        sum(bills.stream().map((bill) -> bill.value().amount()).toList());
    BigDecimal paidTotal =
        sum(
            bills.stream()
                .filter((bill) -> bill.value().paid())
                .map((bill) -> bill.value().amount())
                .toList());
    BigDecimal payPeriodTotal =
        sum(
            bills.stream()
                .filter(FinancialSnapshotCalculation.Bill::inPayPeriod)
                .map((bill) -> bill.value().amount())
                .toList());
    BigDecimal totalAnnualWithdrawals =
        sum(annualWithdrawals.stream().map((annual) -> annual.value().amount()).toList());
    BigDecimal annualPayPeriodTotal =
        sum(
            annualWithdrawals.stream()
                .filter(FinancialSnapshotCalculation.Annual::inPayPeriod)
                .map((annual) -> annual.value().amount())
                .toList());
    BigDecimal totalTrackedAssets =
        sum(
            assetCategories.stream()
                .map(FinancialSnapshotCalculation.AssetCategory::total)
                .toList());
    BigDecimal totalDebt = sum(debtAccounts.stream().map(DebtAccount::amount).toList());

    return new FinancialSnapshotCalculation(
        snapshot.version(),
        payPeriod,
        currentDate,
        snapshot.planningSettings(),
        snapshot.projectionRoles(),
        totalMonthlyExpenses,
        paidTotal,
        totalMonthlyExpenses.subtract(paidTotal),
        payPeriodTotal,
        totalAnnualWithdrawals,
        annualPayPeriodTotal,
        totalTrackedAssets,
        totalDebt,
        totalTrackedAssets.subtract(totalDebt),
        bills,
        annualWithdrawals,
        assetCategories,
        debtAccounts,
        incomeSummaryItems,
        incomeEvents,
        importantDates);
  }

  private List<FinancialSnapshotCalculation.Bill> calculatedBills(
      List<ExpenseBill> source, PayPeriodDatePolicy.PayPeriod payPeriod) {
    return source.stream()
        .sorted(Comparator.comparingInt(ExpenseBill::dueDay))
        .map(
            (bill) -> {
              LocalDate dueDate = PayPeriodDatePolicy.monthlyDueDate(bill.dueDay(), payPeriod);
              return new FinancialSnapshotCalculation.Bill(
                  bill, dueDate, isInPayPeriod(dueDate, payPeriod));
            })
        .toList();
  }

  private List<FinancialSnapshotCalculation.Annual> calculatedAnnualWithdrawals(
      List<AnnualWithdrawal> source, PayPeriodDatePolicy.PayPeriod payPeriod) {
    return source.stream()
        .sorted(
            Comparator.comparingInt(AnnualWithdrawal::month)
                .thenComparingInt(AnnualWithdrawal::day))
        .map(
            (withdrawal) -> {
              LocalDate dueDate =
                  PayPeriodDatePolicy.annualDueDate(
                      withdrawal.month(), withdrawal.day(), payPeriod);
              return new FinancialSnapshotCalculation.Annual(
                  withdrawal, dueDate, isInPayPeriod(dueDate, payPeriod));
            })
        .toList();
  }

  private List<FinancialSnapshotCalculation.AssetCategory> calculatedAssetCategories(
      List<AssetAccount> source) {
    Map<String, List<AssetAccount>> accountsByCategory = new LinkedHashMap<>();
    Map<String, String> labelsByCategory = new LinkedHashMap<>();

    STANDARD_ASSET_CATEGORIES.forEach(
        (category) -> {
          labelsByCategory.put(category.key(), category.label());
          accountsByCategory.put(category.key(), new ArrayList<>());
        });

    source.forEach(
        (account) -> {
          labelsByCategory.putIfAbsent(account.categoryKey(), account.categoryLabel());
          accountsByCategory
              .computeIfAbsent(account.categoryKey(), (key) -> new ArrayList<>())
              .add(account);
        });

    return accountsByCategory.entrySet().stream()
        .map(
            (entry) ->
                new FinancialSnapshotCalculation.AssetCategory(
                    entry.getKey(),
                    labelsByCategory.get(entry.getKey()),
                    sum(entry.getValue().stream().map(AssetAccount::amount).toList()),
                    List.copyOf(entry.getValue())))
        .toList();
  }

  private List<FinancialSnapshotCalculation.Income> calculatedIncomeEvents(
      List<IncomeEvent> source) {
    Map<YearMonth, Long> checksByMonth = new LinkedHashMap<>();
    source.stream()
        .filter((event) -> event.checkNumber() != null)
        .forEach((event) -> checksByMonth.merge(YearMonth.from(event.date()), 1L, Long::sum));

    return source.stream()
        .sorted(Comparator.comparing(IncomeEvent::date))
        .map(
            (event) ->
                new FinancialSnapshotCalculation.Income(
                    event, checksByMonth.getOrDefault(YearMonth.from(event.date()), 0L).intValue()))
        .toList();
  }

  private boolean isInPayPeriod(LocalDate date, PayPeriodDatePolicy.PayPeriod payPeriod) {
    return !date.isBefore(payPeriod.startDate()) && !date.isAfter(payPeriod.endDate());
  }

  private BigDecimal sum(List<BigDecimal> values) {
    return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private record AssetCategoryDefinition(String key, String label) {}
}

package com.example.backend.service;

import com.example.backend.dto.financials.AnnualWithdrawalResponse;
import com.example.backend.dto.financials.AnnualWithdrawalSnapshotRequest;
import com.example.backend.dto.financials.AssetAccountResponse;
import com.example.backend.dto.financials.AssetAccountSnapshotRequest;
import com.example.backend.dto.financials.AssetCategoryResponse;
import com.example.backend.dto.financials.AssetCategorySnapshotRequest;
import com.example.backend.dto.financials.DebtAccountResponse;
import com.example.backend.dto.financials.DebtAccountSnapshotRequest;
import com.example.backend.dto.financials.ExpenseBillRequest;
import com.example.backend.dto.financials.ExpenseBillResponse;
import com.example.backend.dto.financials.ExpenseBillSnapshotRequest;
import com.example.backend.dto.financials.ExpenseSnapshotRequest;
import com.example.backend.dto.financials.ExpenseSnapshotResponse;
import com.example.backend.dto.financials.ImportantDateResponse;
import com.example.backend.dto.financials.ImportantDateSnapshotRequest;
import com.example.backend.dto.financials.IncomeEventResponse;
import com.example.backend.dto.financials.IncomeEventSnapshotRequest;
import com.example.backend.dto.financials.IncomeSummaryItemResponse;
import com.example.backend.dto.financials.IncomeSummaryItemSnapshotRequest;
import com.example.backend.dto.financials.PayPeriodRequest;
import com.example.backend.repository.AnnualWithdrawal;
import com.example.backend.repository.AssetAccount;
import com.example.backend.repository.DebtAccount;
import com.example.backend.repository.ExpenseBill;
import com.example.backend.repository.FinancialsRepository;
import com.example.backend.repository.ImportantDate;
import com.example.backend.repository.IncomeEvent;
import com.example.backend.repository.IncomeSummaryItem;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FinancialsService {

  private static final DateTimeFormatter DISPLAY_DATE_FORMATTER =
      DateTimeFormatter.ofPattern("MM/dd/yyyy");

  private final FinancialsRepository financialsRepository;

  public FinancialsService(FinancialsRepository financialsRepository) {
    this.financialsRepository = financialsRepository;
  }

  public ExpenseSnapshotResponse getSnapshot() {
    LocalDate[] payPeriod = currentPayPeriod();
    LocalDate startDate = payPeriod[0];
    LocalDate endDate = payPeriod[1];

    List<ExpenseBillResponse> bills =
        financialsRepository.findAllBills().stream()
            .map((bill) -> toResponse(bill, startDate, endDate))
            .toList();

    double totalMonthlyExpenses = bills.stream().mapToDouble(ExpenseBillResponse::amount).sum();
    double paidTotal =
        bills.stream()
            .filter(ExpenseBillResponse::paid)
            .mapToDouble(ExpenseBillResponse::amount)
            .sum();
    double unpaidTotal = totalMonthlyExpenses - paidTotal;
    double payPeriodTotal =
        bills.stream()
            .filter(ExpenseBillResponse::inPayPeriod)
            .mapToDouble(ExpenseBillResponse::amount)
            .sum();
    List<AnnualWithdrawalResponse> annualWithdrawals =
        financialsRepository.findAllAnnualWithdrawals().stream()
            .map((withdrawal) -> toResponse(withdrawal, startDate, endDate))
            .toList();
    double totalAnnualWithdrawals =
        annualWithdrawals.stream().mapToDouble(AnnualWithdrawalResponse::amount).sum();
    double annualPayPeriodTotal =
        annualWithdrawals.stream()
            .filter(AnnualWithdrawalResponse::inPayPeriod)
            .mapToDouble(AnnualWithdrawalResponse::amount)
            .sum();
    List<AssetCategoryResponse> assetCategories = assetCategories();
    double totalTrackedAssets =
        assetCategories.stream().mapToDouble(AssetCategoryResponse::total).sum();
    List<DebtAccountResponse> debtAccounts = debtAccounts();
    double totalDebt = debtAccounts.stream().mapToDouble(DebtAccountResponse::amount).sum();
    double netWorth = totalTrackedAssets - totalDebt;
    List<IncomeSummaryItemResponse> incomeSummaryItems = incomeSummaryItems();
    List<IncomeEventResponse> incomeEvents = incomeEvents();
    List<ImportantDateResponse> importantDates = importantDates();

    return new ExpenseSnapshotResponse(
        startDate,
        endDate,
        totalMonthlyExpenses,
        paidTotal,
        unpaidTotal,
        payPeriodTotal,
        totalAnnualWithdrawals,
        annualPayPeriodTotal,
        totalTrackedAssets,
        totalDebt,
        netWorth,
        assetCategories,
        debtAccounts,
        incomeSummaryItems,
        bills,
        annualWithdrawals,
        incomeEvents,
        importantDates);
  }

  public ExpenseBillResponse addBill(ExpenseBillRequest request) {
    ExpenseBill created = financialsRepository.addBill(toBill(request, 0));
    LocalDate[] payPeriod = currentPayPeriod();
    return toResponse(created, payPeriod[0], payPeriod[1]);
  }

  public ExpenseBillResponse updateBill(long id, ExpenseBillRequest request) {
    ExpenseBill bill = toBill(request, id);
    ExpenseBill updated =
        financialsRepository
            .updateBill(id, bill)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bill not found"));
    LocalDate[] payPeriod = currentPayPeriod();
    return toResponse(updated, payPeriod[0], payPeriod[1]);
  }

  public void deleteBill(long id) {
    if (!financialsRepository.deleteBill(id)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Bill not found");
    }
  }

  public ExpenseSnapshotResponse updatePayPeriod(PayPeriodRequest request) {
    validatePayPeriod(request.startDate(), request.endDate());
    financialsRepository.updatePayPeriod(request.startDate(), request.endDate());
    return getSnapshot();
  }

  public ExpenseSnapshotResponse saveSnapshot(ExpenseSnapshotRequest request) {
    validatePayPeriod(request.payPeriodStart(), request.payPeriodEnd());
    List<ExpenseBill> bills = request.bills().stream().map(this::toBill).toList();
    List<AnnualWithdrawal> annualWithdrawals =
        nullSafe(request.annualWithdrawals()).stream().map(this::toAnnualWithdrawal).toList();
    List<AssetAccount> assetAccounts =
        request.assetCategories().stream()
            .flatMap((category) -> toAssetAccounts(category).stream())
            .toList();
    List<DebtAccount> debtAccounts =
        nullSafe(request.debtAccounts()).stream().map(this::toDebtAccount).toList();
    List<IncomeSummaryItem> incomeSummaryItems =
        nullSafe(request.incomeSummaryItems()).stream().map(this::toIncomeSummaryItem).toList();
    List<IncomeEvent> incomeEvents =
        request.incomeEvents().stream().map(this::toIncomeEvent).toList();
    List<ImportantDate> importantDates =
        request.importantDates().stream().map(this::toImportantDate).toList();
    financialsRepository.replaceSnapshot(
        request.payPeriodStart(),
        request.payPeriodEnd(),
        bills,
        annualWithdrawals,
        assetAccounts,
        debtAccounts,
        incomeSummaryItems,
        incomeEvents,
        importantDates);
    return getSnapshot();
  }

  private ExpenseBill toBill(ExpenseBillRequest request, long id) {
    validateBill(request.bill(), request.dueDay(), request.amount(), request.account());
    return new ExpenseBill(
        id,
        request.bill().trim(),
        request.dueDay(),
        request.amount(),
        request.account().trim(),
        request.paid());
  }

  private ExpenseBill toBill(ExpenseBillSnapshotRequest request) {
    validateBill(request.bill(), request.dueDay(), request.amount(), request.account());
    long id = request.id() == null ? 0 : request.id();
    return new ExpenseBill(
        id,
        request.bill().trim(),
        request.dueDay(),
        request.amount(),
        request.account().trim(),
        request.paid());
  }

  private void validateBill(String bill, int dueDay, double amount, String account) {
    if (bill == null || bill.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bill name is required");
    }

    if (account == null || account.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account is required");
    }

    if (dueDay < 1 || dueDay > 31) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Due day must be between 1 and 31");
    }

    if (amount < 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be positive");
    }
  }

  private AnnualWithdrawal toAnnualWithdrawal(AnnualWithdrawalSnapshotRequest request) {
    validateAnnualWithdrawal(
        request.bill(), request.month(), request.day(), request.amount(), request.account());
    long id = request.id() == null ? 0 : request.id();
    return new AnnualWithdrawal(
        id,
        request.bill().trim(),
        request.month(),
        request.day(),
        request.amount(),
        request.account().trim(),
        request.paid());
  }

  private void validateAnnualWithdrawal(
      String bill, int month, int day, double amount, String account) {
    if (bill == null || bill.isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Annual withdrawal name is required");
    }

    if (account == null || account.isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Annual withdrawal account is required");
    }

    if (month < 1 || month > 12) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Month must be between 1 and 12");
    }

    if (day < 1 || day > YearMonth.of(2024, month).lengthOfMonth()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Day is not valid for month");
    }

    if (amount < 0) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Annual withdrawal amount must be positive");
    }
  }

  private void validatePayPeriod(LocalDate startDate, LocalDate endDate) {
    if (endDate.isBefore(startDate)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Pay period end date must be on or after start date");
    }
  }

  private List<AssetAccount> toAssetAccounts(AssetCategorySnapshotRequest category) {
    if (category.key() == null || category.key().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Asset category key is required");
    }

    if (category.label() == null || category.label().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Asset category label is required");
    }

    return category.accounts().stream()
        .map((account) -> toAssetAccount(category, account))
        .toList();
  }

  private AssetAccount toAssetAccount(
      AssetCategorySnapshotRequest category, AssetAccountSnapshotRequest account) {
    validateAssetAccount(account.account(), account.company(), account.amount());
    long id = account.id() == null ? 0 : account.id();
    return new AssetAccount(
        id,
        category.key().trim(),
        category.label().trim(),
        account.account().trim(),
        account.company().trim(),
        account.amount());
  }

  private void validateAssetAccount(String account, String company, double amount) {
    if (account == null || account.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Asset account is required");
    }

    if (company == null || company.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Asset company is required");
    }

    if (amount < 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Asset amount must be positive");
    }
  }

  private DebtAccount toDebtAccount(DebtAccountSnapshotRequest request) {
    validateDebtAccount(request.account(), request.company(), request.amount());
    long id = request.id() == null ? 0 : request.id();
    return new DebtAccount(
        id, request.account().trim(), request.company().trim(), request.amount());
  }

  private void validateDebtAccount(String account, String company, double amount) {
    if (account == null || account.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debt account is required");
    }

    if (company == null || company.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debt company is required");
    }

    if (amount < 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debt amount must be positive");
    }
  }

  private IncomeSummaryItem toIncomeSummaryItem(IncomeSummaryItemSnapshotRequest request) {
    validateIncomeSummaryItem(request.category(), request.interval(), request.amount());
    long id = request.id() == null ? 0 : request.id();
    return new IncomeSummaryItem(
        id, request.category().trim(), request.interval().trim(), request.amount());
  }

  private void validateIncomeSummaryItem(String category, String interval, double amount) {
    if (category == null || category.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Income category is required");
    }

    if (interval == null || interval.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Income interval is required");
    }

    if (amount < 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Income amount must be positive");
    }
  }

  private IncomeEvent toIncomeEvent(IncomeEventSnapshotRequest request) {
    validateIncomeEvent(request.date(), request.label(), request.type(), request.checkNumber());
    long id = request.id() == null ? 0 : request.id();
    return new IncomeEvent(
        id, request.date(), request.label().trim(), request.type().trim(), request.checkNumber());
  }

  private void validateIncomeEvent(LocalDate date, String label, String type, Integer checkNumber) {
    if (date == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Income date is required");
    }

    if (label == null || label.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Income label is required");
    }

    if (type == null || type.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Income type is required");
    }

    if (checkNumber != null && checkNumber < 1) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Check number must be greater than zero");
    }
  }

  private ImportantDate toImportantDate(ImportantDateSnapshotRequest request) {
    validateImportantDate(request.date(), request.event(), request.type());
    long id = request.id() == null ? 0 : request.id();
    return new ImportantDate(id, request.date(), request.event().trim(), request.type().trim());
  }

  private void validateImportantDate(LocalDate date, String event, String type) {
    if (date == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Important date is required");
    }

    if (event == null || event.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Important date event is required");
    }

    if (type == null || type.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Important date type is required");
    }
  }

  private ExpenseBillResponse toResponse(ExpenseBill bill, LocalDate startDate, LocalDate endDate) {
    LocalDate dueDate = dueDateForPayPeriodMonth(bill.dueDay(), startDate, endDate);
    boolean inPayPeriod = !dueDate.isBefore(startDate) && !dueDate.isAfter(endDate);

    return new ExpenseBillResponse(
        bill.id(),
        bill.bill(),
        bill.dueDay(),
        ordinal(bill.dueDay()),
        dueDate,
        bill.amount(),
        bill.account(),
        bill.paid(),
        inPayPeriod);
  }

  private AnnualWithdrawalResponse toResponse(
      AnnualWithdrawal withdrawal, LocalDate startDate, LocalDate endDate) {
    LocalDate dueDate = annualDueDate(withdrawal.month(), withdrawal.day(), startDate, endDate);
    boolean inPayPeriod = !dueDate.isBefore(startDate) && !dueDate.isAfter(endDate);

    return new AnnualWithdrawalResponse(
        withdrawal.id(),
        withdrawal.bill(),
        withdrawal.month(),
        withdrawal.day(),
        dateLabel(dueDate),
        dueDate,
        withdrawal.amount(),
        withdrawal.account(),
        withdrawal.paid(),
        inPayPeriod);
  }

  private List<AssetCategoryResponse> assetCategories() {
    Map<String, List<AssetAccountResponse>> accountsByCategory = new LinkedHashMap<>();
    Map<String, String> labelsByCategory = new LinkedHashMap<>();

    financialsRepository
        .findAllAssetAccounts()
        .forEach(
            (account) -> {
              labelsByCategory.putIfAbsent(account.categoryKey(), account.categoryLabel());
              accountsByCategory
                  .computeIfAbsent(account.categoryKey(), (key) -> new java.util.ArrayList<>())
                  .add(
                      new AssetAccountResponse(
                          account.id(), account.account(), account.company(), account.amount()));
            });

    return accountsByCategory.entrySet().stream()
        .map(
            (entry) -> {
              double total =
                  entry.getValue().stream().mapToDouble(AssetAccountResponse::amount).sum();
              return new AssetCategoryResponse(
                  entry.getKey(), labelsByCategory.get(entry.getKey()), total, entry.getValue());
            })
        .toList();
  }

  private List<IncomeEventResponse> incomeEvents() {
    Map<YearMonth, Long> paycheckCountsByMonth = paycheckCountsByMonth();

    return financialsRepository.findAllIncomeEvents().stream()
        .map(
            (event) ->
                new IncomeEventResponse(
                    event.id(),
                    event.date(),
                    event.label(),
                    event.type(),
                    event.checkNumber(),
                    paycheckCountsByMonth
                        .getOrDefault(YearMonth.from(event.date()), 0L)
                        .intValue()))
        .toList();
  }

  private List<DebtAccountResponse> debtAccounts() {
    return financialsRepository.findAllDebtAccounts().stream()
        .map(
            (account) ->
                new DebtAccountResponse(
                    account.id(), account.account(), account.company(), account.amount()))
        .toList();
  }

  private List<IncomeSummaryItemResponse> incomeSummaryItems() {
    return financialsRepository.findAllIncomeSummaryItems().stream()
        .map(
            (item) ->
                new IncomeSummaryItemResponse(
                    item.id(), item.category(), item.interval(), item.amount()))
        .toList();
  }

  private Map<YearMonth, Long> paycheckCountsByMonth() {
    Map<YearMonth, Long> counts = new LinkedHashMap<>();
    financialsRepository.findAllIncomeEvents().stream()
        .filter((event) -> event.checkNumber() != null)
        .forEach((event) -> counts.merge(YearMonth.from(event.date()), 1L, Long::sum));
    return counts;
  }

  private List<ImportantDateResponse> importantDates() {
    return financialsRepository.findAllImportantDates().stream()
        .map(
            (importantDate) ->
                new ImportantDateResponse(
                    importantDate.id(),
                    importantDate.date(),
                    importantDate.event(),
                    importantDate.type()))
        .toList();
  }

  private LocalDate[] currentPayPeriod() {
    LocalDate startDate = financialsRepository.payPeriodStart();
    LocalDate endDate = financialsRepository.payPeriodEnd();
    LocalDate today = LocalDate.now();
    long periodDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;

    while (today.isAfter(endDate)) {
      startDate = startDate.plusDays(periodDays);
      endDate = endDate.plusDays(periodDays);
    }

    while (today.isBefore(startDate)) {
      startDate = startDate.minusDays(periodDays);
      endDate = endDate.minusDays(periodDays);
    }

    return new LocalDate[] {startDate, endDate};
  }

  private LocalDate dueDateForPayPeriodMonth(int dueDay, LocalDate startDate, LocalDate endDate) {
    LocalDate dueDate = safeDate(startDate.getYear(), startDate.getMonthValue(), dueDay);

    if (dueDate.isBefore(startDate) && startDate.getMonthValue() != endDate.getMonthValue()) {
      return safeDate(endDate.getYear(), endDate.getMonthValue(), dueDay);
    }

    return dueDate;
  }

  private LocalDate safeDate(int year, int month, int day) {
    LocalDate firstOfMonth = LocalDate.of(year, month, 1);
    int safeDay = Math.min(day, firstOfMonth.lengthOfMonth());
    return LocalDate.of(year, month, safeDay);
  }

  private LocalDate annualDueDate(int month, int day, LocalDate startDate, LocalDate endDate) {
    LocalDate dueDate = safeDate(startDate.getYear(), month, day);

    if (dueDate.isBefore(startDate) && startDate.getYear() != endDate.getYear()) {
      return safeDate(endDate.getYear(), month, day);
    }

    return dueDate;
  }

  private String dateLabel(LocalDate date) {
    return date.format(DISPLAY_DATE_FORMATTER);
  }

  private String ordinal(int day) {
    if (day >= 11 && day <= 13) {
      return day + "th";
    }

    return switch (day % 10) {
      case 1 -> day + "st";
      case 2 -> day + "nd";
      case 3 -> day + "rd";
      default -> day + "th";
    };
  }

  private <T> List<T> nullSafe(List<T> value) {
    return value == null ? List.of() : value;
  }
}

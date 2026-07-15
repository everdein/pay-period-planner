package com.example.backend.service;

import com.example.backend.domain.financials.AnnualWithdrawal;
import com.example.backend.domain.financials.AssetAccount;
import com.example.backend.domain.financials.DebtAccount;
import com.example.backend.domain.financials.ExpenseBill;
import com.example.backend.domain.financials.FinancialPlanningSettings;
import com.example.backend.domain.financials.FinancialProjectionRoles;
import com.example.backend.domain.financials.FinancialSnapshot;
import com.example.backend.domain.financials.ImportantDate;
import com.example.backend.domain.financials.IncomeEvent;
import com.example.backend.domain.financials.IncomeSummaryItem;
import com.example.backend.dto.financials.AnnualWithdrawalSnapshotRequest;
import com.example.backend.dto.financials.AssetAccountSnapshotRequest;
import com.example.backend.dto.financials.AssetCategorySnapshotRequest;
import com.example.backend.dto.financials.DebtAccountSnapshotRequest;
import com.example.backend.dto.financials.ExpenseBillSnapshotRequest;
import com.example.backend.dto.financials.ExpenseSnapshotRequest;
import com.example.backend.dto.financials.FinancialPlanningSettingsRequest;
import com.example.backend.dto.financials.FinancialProjectionRolesRequest;
import com.example.backend.dto.financials.ImportantDateSnapshotRequest;
import com.example.backend.dto.financials.IncomeEventSnapshotRequest;
import com.example.backend.dto.financials.IncomeSummaryItemSnapshotRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class FinancialSnapshotRequestMapper {

  private final FinancialSnapshotNormalizer normalizer;

  public FinancialSnapshotRequestMapper(FinancialSnapshotNormalizer normalizer) {
    this.normalizer = normalizer;
  }

  long validateSnapshotVersion(Long version) {
    if (version == null || version < 1) {
      throw new FinancialRequestException("Snapshot version must be a positive number");
    }

    return version;
  }

  FinancialSnapshot toDomainSnapshot(ExpenseSnapshotRequest request) {
    validatePayPeriod(request.payPeriodStart(), request.payPeriodEnd());
    return normalizer.normalize(
        new FinancialSnapshot(
            request.version(),
            request.payPeriodStart(),
            request.payPeriodEnd(),
            toPlanningSettings(request.planningSettings()),
            toProjectionRoles(request.projectionRoles()),
            request.bills().stream().map(this::toBill).toList(),
            nullSafe(request.annualWithdrawals()).stream().map(this::toAnnualWithdrawal).toList(),
            request.assetCategories().stream()
                .flatMap((category) -> toAssetAccounts(category).stream())
                .toList(),
            nullSafe(request.debtAccounts()).stream().map(this::toDebtAccount).toList(),
            nullSafe(request.incomeSummaryItems()).stream().map(this::toIncomeSummaryItem).toList(),
            request.incomeEvents().stream().map(this::toIncomeEvent).toList(),
            request.importantDates().stream().map(this::toImportantDate).toList()));
  }

  ExpenseSnapshotRequest toSnapshotRequest(FinancialSnapshot snapshot) {
    return new ExpenseSnapshotRequest(
        snapshot.version(),
        snapshot.payPeriodStart(),
        snapshot.payPeriodEnd(),
        toSnapshotRequest(snapshot.planningSettings()),
        toSnapshotRequest(snapshot.projectionRoles()),
        snapshot.bills().stream().map(this::toSnapshotRequest).toList(),
        snapshot.annualWithdrawals().stream().map(this::toSnapshotRequest).toList(),
        toSnapshotAssetCategories(snapshot.assetAccounts()),
        snapshot.debtAccounts().stream().map(this::toSnapshotRequest).toList(),
        snapshot.incomeSummaryItems().stream().map(this::toSnapshotRequest).toList(),
        snapshot.incomeEvents().stream().map(this::toSnapshotRequest).toList(),
        snapshot.importantDates().stream().map(this::toSnapshotRequest).toList());
  }

  ExpenseSnapshotRequest withVersion(ExpenseSnapshotRequest snapshot, long version) {
    return new ExpenseSnapshotRequest(
        version,
        snapshot.payPeriodStart(),
        snapshot.payPeriodEnd(),
        snapshot.planningSettings(),
        snapshot.projectionRoles(),
        snapshot.bills(),
        snapshot.annualWithdrawals(),
        snapshot.assetCategories(),
        snapshot.debtAccounts(),
        snapshot.incomeSummaryItems(),
        snapshot.incomeEvents(),
        snapshot.importantDates());
  }

  FinancialPlanningSettings toPlanningSettings(FinancialPlanningSettingsRequest settings) {
    if (settings == null) {
      return null;
    }

    try {
      return FinancialPlanningSettings.from(settings.payCadence(), settings.timeZone());
    } catch (IllegalArgumentException exception) {
      throw new FinancialRequestException(exception.getMessage());
    }
  }

  private FinancialPlanningSettingsRequest toSnapshotRequest(FinancialPlanningSettings settings) {
    return settings == null
        ? null
        : new FinancialPlanningSettingsRequest(settings.payCadence().name(), settings.timeZone());
  }

  private FinancialProjectionRoles toProjectionRoles(FinancialProjectionRolesRequest roles) {
    return roles == null
        ? null
        : new FinancialProjectionRoles(
            roles.rentBillId(),
            roles.rentReserveAssetAccountId(),
            roles.primaryPaycheckIncomeSummaryItemId());
  }

  private FinancialProjectionRolesRequest toSnapshotRequest(FinancialProjectionRoles roles) {
    return roles == null
        ? null
        : new FinancialProjectionRolesRequest(
            roles.rentBillId(),
            roles.rentReserveAssetAccountId(),
            roles.primaryPaycheckIncomeSummaryItemId());
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

  private List<AssetAccount> toAssetAccounts(AssetCategorySnapshotRequest category) {
    if (category.key() == null || category.key().isBlank()) {
      throw new FinancialRequestException("Asset category key is required");
    }

    if (category.label() == null || category.label().isBlank()) {
      throw new FinancialRequestException("Asset category label is required");
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

  private DebtAccount toDebtAccount(DebtAccountSnapshotRequest request) {
    validateDebtAccount(request.account(), request.company(), request.amount());
    long id = request.id() == null ? 0 : request.id();
    return new DebtAccount(
        id, request.account().trim(), request.company().trim(), request.amount());
  }

  private IncomeSummaryItem toIncomeSummaryItem(IncomeSummaryItemSnapshotRequest request) {
    validateIncomeSummaryItem(request.category(), request.interval(), request.amount());
    long id = request.id() == null ? 0 : request.id();
    return new IncomeSummaryItem(
        id, request.category().trim(), request.interval().trim(), request.amount());
  }

  private IncomeEvent toIncomeEvent(IncomeEventSnapshotRequest request) {
    validateIncomeEvent(request.date(), request.label(), request.type(), request.checkNumber());
    long id = request.id() == null ? 0 : request.id();
    return new IncomeEvent(
        id, request.date(), request.label().trim(), request.type().trim(), request.checkNumber());
  }

  private ImportantDate toImportantDate(ImportantDateSnapshotRequest request) {
    validateImportantDate(request.date(), request.event(), request.type());
    long id = request.id() == null ? 0 : request.id();
    return new ImportantDate(id, request.date(), request.event().trim(), request.type().trim());
  }

  private ExpenseBillSnapshotRequest toSnapshotRequest(ExpenseBill bill) {
    return new ExpenseBillSnapshotRequest(
        bill.id(), bill.bill(), bill.dueDay(), bill.amount(), bill.account(), bill.paid());
  }

  private AnnualWithdrawalSnapshotRequest toSnapshotRequest(AnnualWithdrawal withdrawal) {
    return new AnnualWithdrawalSnapshotRequest(
        withdrawal.id(),
        withdrawal.bill(),
        withdrawal.month(),
        withdrawal.day(),
        withdrawal.amount(),
        withdrawal.account(),
        withdrawal.paid());
  }

  private List<AssetCategorySnapshotRequest> toSnapshotAssetCategories(
      List<AssetAccount> accounts) {
    Map<String, String> labelsByCategory = new LinkedHashMap<>();
    Map<String, List<AssetAccountSnapshotRequest>> accountsByCategory = new LinkedHashMap<>();

    accounts.forEach(
        (account) -> {
          labelsByCategory.putIfAbsent(account.categoryKey(), account.categoryLabel());
          accountsByCategory
              .computeIfAbsent(account.categoryKey(), (key) -> new ArrayList<>())
              .add(
                  new AssetAccountSnapshotRequest(
                      account.id(), account.account(), account.company(), account.amount()));
        });

    return accountsByCategory.entrySet().stream()
        .map(
            (entry) ->
                new AssetCategorySnapshotRequest(
                    entry.getKey(), labelsByCategory.get(entry.getKey()), entry.getValue()))
        .toList();
  }

  private DebtAccountSnapshotRequest toSnapshotRequest(DebtAccount account) {
    return new DebtAccountSnapshotRequest(
        account.id(), account.account(), account.company(), account.amount());
  }

  private IncomeSummaryItemSnapshotRequest toSnapshotRequest(IncomeSummaryItem item) {
    return new IncomeSummaryItemSnapshotRequest(
        item.id(), item.category(), item.interval(), item.amount());
  }

  private IncomeEventSnapshotRequest toSnapshotRequest(IncomeEvent event) {
    return new IncomeEventSnapshotRequest(
        event.id(), event.date(), event.label(), event.type(), event.checkNumber());
  }

  private ImportantDateSnapshotRequest toSnapshotRequest(ImportantDate importantDate) {
    return new ImportantDateSnapshotRequest(
        importantDate.id(), importantDate.date(), importantDate.event(), importantDate.type());
  }

  private void validateBill(String bill, int dueDay, BigDecimal amount, String account) {
    if (bill == null || bill.isBlank()) {
      throw new FinancialRequestException("Bill name is required");
    }
    if (account == null || account.isBlank()) {
      throw new FinancialRequestException("Account is required");
    }
    if (dueDay < 1 || dueDay > 31) {
      throw new FinancialRequestException("Due day must be between 1 and 31");
    }
    if (amount == null || isNegative(amount)) {
      throw new FinancialRequestException("Amount must be positive");
    }
  }

  private void validateAnnualWithdrawal(
      String bill, int month, int day, BigDecimal amount, String account) {
    if (bill == null || bill.isBlank()) {
      throw new FinancialRequestException("Annual withdrawal name is required");
    }
    if (account == null || account.isBlank()) {
      throw new FinancialRequestException("Annual withdrawal account is required");
    }
    if (month < 1 || month > 12) {
      throw new FinancialRequestException("Month must be between 1 and 12");
    }
    if (day < 1 || day > YearMonth.of(2024, month).lengthOfMonth()) {
      throw new FinancialRequestException("Day is not valid for month");
    }
    if (amount == null || isNegative(amount)) {
      throw new FinancialRequestException("Annual withdrawal amount must be positive");
    }
  }

  private void validatePayPeriod(LocalDate startDate, LocalDate endDate) {
    if (endDate.isBefore(startDate)) {
      throw new FinancialRequestException("Pay period end date must be on or after start date");
    }
  }

  private void validateAssetAccount(String account, String company, BigDecimal amount) {
    if (account == null || account.isBlank()) {
      throw new FinancialRequestException("Asset account is required");
    }
    if (company == null || company.isBlank()) {
      throw new FinancialRequestException("Asset company is required");
    }
    if (amount == null || isNegative(amount)) {
      throw new FinancialRequestException("Asset amount must be positive");
    }
  }

  private void validateDebtAccount(String account, String company, BigDecimal amount) {
    if (account == null || account.isBlank()) {
      throw new FinancialRequestException("Debt account is required");
    }
    if (company == null || company.isBlank()) {
      throw new FinancialRequestException("Debt company is required");
    }
    if (amount == null || isNegative(amount)) {
      throw new FinancialRequestException("Debt amount must be positive");
    }
  }

  private void validateIncomeSummaryItem(String category, String interval, BigDecimal amount) {
    if (category == null || category.isBlank()) {
      throw new FinancialRequestException("Income category is required");
    }
    if (interval == null || interval.isBlank()) {
      throw new FinancialRequestException("Income interval is required");
    }
    if (amount == null || isNegative(amount)) {
      throw new FinancialRequestException("Income amount must be positive");
    }
  }

  private void validateIncomeEvent(LocalDate date, String label, String type, Integer checkNumber) {
    if (date == null) {
      throw new FinancialRequestException("Income date is required");
    }
    if (label == null || label.isBlank()) {
      throw new FinancialRequestException("Income label is required");
    }
    if (type == null || type.isBlank()) {
      throw new FinancialRequestException("Income type is required");
    }
    if (checkNumber != null && checkNumber < 1) {
      throw new FinancialRequestException("Check number must be greater than zero");
    }
  }

  private void validateImportantDate(LocalDate date, String event, String type) {
    if (date == null) {
      throw new FinancialRequestException("Important date is required");
    }
    if (event == null || event.isBlank()) {
      throw new FinancialRequestException("Important date event is required");
    }
    if (type == null || type.isBlank()) {
      throw new FinancialRequestException("Important date type is required");
    }
  }

  private boolean isNegative(BigDecimal amount) {
    return amount != null && amount.signum() < 0;
  }

  private <T> List<T> nullSafe(List<T> value) {
    return value == null ? List.of() : value;
  }
}

package com.example.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.backend.dto.financials.AnnualWithdrawalRequest;
import com.example.backend.dto.financials.AnnualWithdrawalSnapshotRequest;
import com.example.backend.dto.financials.AssetAccountRequest;
import com.example.backend.dto.financials.AssetAccountSnapshotRequest;
import com.example.backend.dto.financials.AssetCategorySnapshotRequest;
import com.example.backend.dto.financials.DebtAccountRequest;
import com.example.backend.dto.financials.DebtAccountSnapshotRequest;
import com.example.backend.dto.financials.ExpenseBillRequest;
import com.example.backend.dto.financials.ExpenseBillSnapshotRequest;
import com.example.backend.dto.financials.ExpenseSnapshotRequest;
import com.example.backend.dto.financials.ImportantDateRequest;
import com.example.backend.dto.financials.ImportantDateSnapshotRequest;
import com.example.backend.dto.financials.IncomeEventRequest;
import com.example.backend.dto.financials.IncomeEventSnapshotRequest;
import com.example.backend.dto.financials.IncomeSummaryItemRequest;
import com.example.backend.dto.financials.IncomeSummaryItemSnapshotRequest;
import com.example.backend.dto.financials.PayPeriodRequest;
import com.example.backend.repository.FinancialsRepository;
import com.example.backend.repository.JsonFinancialsSnapshotStore;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

class FinancialsServiceTests {

  @TempDir private Path tempDir;

  @Test
  void returnsSeededMonthlyExpenseSnapshot() throws IOException {
    FinancialsService service = new FinancialsService(repository());

    var snapshot = service.getSnapshot();

    assertThat(snapshot.version()).isEqualTo(1);
    assertThat(snapshot.totalMonthlyExpenses()).isEqualByComparingTo("1450.00");
    assertThat(snapshot.totalAnnualWithdrawals()).isEqualByComparingTo("99.00");
    assertThat(snapshot.totalTrackedAssets()).isEqualByComparingTo("15000.00");
    assertThat(snapshot.totalDebt()).isEqualByComparingTo("500.00");
    assertThat(snapshot.netWorth()).isEqualByComparingTo("14500.00");
    assertThat(snapshot.bills()).isNotEmpty();
    assertThat(snapshot.annualWithdrawals()).hasSize(1);
    assertThat(snapshot.debtAccounts()).hasSize(1);
    assertThat(snapshot.assetCategories()).hasSize(2);
    assertThat(snapshot.incomeSummaryItems()).hasSize(2);
    assertThat(snapshot.incomeEvents()).hasSize(2);
    assertThat(snapshot.importantDates()).hasSize(1);
    assertThat(snapshot.bills()).anyMatch((bill) -> bill.bill().equals("Rent"));
    assertThat(snapshot.assetCategories())
        .anyMatch(
            (category) ->
                category.key().equals("cash-savings")
                    && category.accounts().stream()
                        .anyMatch((account) -> account.account().equals("Rent Reserve")));
    assertThat(snapshot.incomeSummaryItems())
        .anyMatch(
            (item) -> item.category().equals("Net Income") && item.interval().equals("Bi-Weekly"));
  }

  @Test
  void exportsSourceSnapshotForBackup() throws IOException {
    Clock clock = Clock.fixed(Instant.parse("2026-07-11T10:15:30Z"), ZoneOffset.UTC);
    FinancialsService service = new FinancialsService(repository(), clock);

    var backup = service.exportSnapshot();

    assertThat(backup.format()).isEqualTo("end-to-end-app.financial-snapshot.v1");
    assertThat(backup.exportedAt()).isEqualTo(Instant.parse("2026-07-11T10:15:30Z"));
    assertThat(backup.snapshot().version()).isEqualTo(1);
    assertThat(backup.snapshot().payPeriodStart()).isEqualTo(LocalDate.of(2026, 1, 1));
    assertThat(backup.snapshot().payPeriodEnd()).isEqualTo(LocalDate.of(2026, 1, 15));
    assertThat(backup.snapshot().bills())
        .anyMatch((bill) -> bill.id() == 1L && bill.bill().equals("Example Rent"));
    assertThat(backup.snapshot().assetCategories())
        .anyMatch(
            (category) ->
                category.key().equals("cash-savings")
                    && category.accounts().stream()
                        .anyMatch((account) -> account.account().equals("Emergency Fund")));
    assertThat(backup.snapshot().incomeEvents())
        .anyMatch((event) -> event.label().equals("Paycheck") && event.checkNumber() == 1);
  }

  @Test
  void exportsAndImportsSourceSnapshotAsCsv() throws IOException {
    FinancialsService service = new FinancialsService(repository());

    var export = service.exportSnapshotCsv();
    String csv = new String(export.content(), StandardCharsets.UTF_8);
    var imported = service.importSnapshotCsv(csv);

    assertThat(export.version()).isEqualTo(1);
    assertThat(csv).startsWith("recordType,version,id,payPeriodStart,payPeriodEnd");
    assertThat(csv).contains("snapshot,1,,2026-01-01,2026-01-15");
    assertThat(csv).contains("bill,,1,,,Example Rent,1");
    assertThat(imported.version()).isEqualTo(2);
    assertThat(imported.bills()).anyMatch((bill) -> bill.bill().equals("Rent"));
    assertThat(imported.assetCategories())
        .anyMatch(
            (category) ->
                category.key().equals("cash-savings")
                    && category.accounts().stream()
                        .anyMatch((account) -> account.account().equals("Emergency Fund")));
  }

  @Test
  void exportsAndImportsSourceSnapshotAsXlsx() throws IOException {
    FinancialsService service = new FinancialsService(repository());

    var export = service.exportSnapshotXlsx();
    var imported = service.importSnapshotXlsx(export.content());

    assertThat(export.version()).isEqualTo(1);
    assertThat(export.content()).startsWith((byte) 'P', (byte) 'K');
    assertThat(imported.version()).isEqualTo(2);
    assertThat(imported.incomeEvents())
        .anyMatch((event) -> event.label().equals("Paycheck") && event.checkNumber() == 1);
    assertThat(imported.importantDates())
        .anyMatch((date) -> date.event().equals("New Years") && date.type().equals("Holiday"));
  }

  @Test
  void rejectsInvalidTabularImport() throws IOException {
    FinancialsService service = new FinancialsService(repository());

    assertThatThrownBy(() -> service.importSnapshotCsv("wrong,header\n"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            (exception) ->
                assertThat(((ResponseStatusException) exception).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST));
  }

  @Test
  void createsUpdatesAndDeletesBill() throws IOException {
    FinancialsService service = new FinancialsService(repository());

    var created =
        service.addBill(new ExpenseBillRequest("Test Bill", 5, money("42.50"), "Check", false));
    assertThat(created.bill()).isEqualTo("Test Bill");
    assertThat(created.dueLabel()).isEqualTo("5th");

    var updated =
        service.updateBill(
            created.id(), new ExpenseBillRequest("Updated Bill", 6, money("45"), "Apple", true));
    assertThat(updated.bill()).isEqualTo("Updated Bill");
    assertThat(updated.amount()).isEqualByComparingTo("45");

    service.deleteBill(created.id());
    assertThat(service.getSnapshot().bills()).noneMatch((bill) -> bill.id() == created.id());
  }

  @Test
  void recordsAuditHistoryWithProjectionSummary() throws IOException {
    FinancialsService service = new FinancialsService(repository());
    long loadedVersion = service.getSnapshot().version();

    var created =
        service.addBill(new ExpenseBillRequest("Audit Bill", 5, money("42.50"), "Check", false));
    var history = service.getAuditHistory(10);

    assertThat(history.events()).hasSize(1);
    var event = history.events().getFirst();
    assertThat(event.action()).isEqualTo("CREATE");
    assertThat(event.resourceType()).isEqualTo("monthly-bill");
    assertThat(event.resourceId()).isEqualTo(created.id());
    assertThat(event.versionBefore()).isEqualTo(loadedVersion);
    assertThat(event.versionAfter()).isEqualTo(loadedVersion + 1);
    assertThat(event.summary()).isEqualTo("Created monthly bill");
    assertThat(event.projectionSummary().monthlyBillCount()).isEqualTo(3);
    assertThat(event.projectionSummary().totalMonthlyExpenses()).isEqualByComparingTo("1492.50");
    assertThat(event.projectionSummary().totalTrackedAssets()).isEqualByComparingTo("15000.0");
    assertThat(event.projectionSummary().totalDebt()).isEqualByComparingTo("500.0");
    assertThat(event.projectionSummary().netWorth()).isEqualByComparingTo("14500.0");
    assertThat(Files.readString(tempDir.resolve("financials.local.json")))
        .contains("\"auditEvents\"");
  }

  @Test
  void createsUpdatesAndDeletesGranularFinancialRecords() throws IOException {
    FinancialsService service = new FinancialsService(repository());

    var annualWithdrawal =
        service.addAnnualWithdrawal(
            new AnnualWithdrawalRequest("Insurance", 4, 15, money("120"), "Check", false));
    var updatedAnnualWithdrawal =
        service.updateAnnualWithdrawal(
            annualWithdrawal.id(),
            new AnnualWithdrawalRequest("Insurance Renewal", 5, 16, money("130"), "Apple", true));

    var assetAccount =
        service.addAssetAccount(
            new AssetAccountRequest(
                "cash-savings", "Cash & Savings", "Vacation", "Credit Union", money("900")));
    var updatedAssetAccount =
        service.updateAssetAccount(
            assetAccount.id(),
            new AssetAccountRequest(
                "investments", "Investments", "Brokerage", "Example", money("1250")));

    var debtAccount =
        service.addDebtAccount(new DebtAccountRequest("Student Loan", "Servicer", money("3500")));
    var updatedDebtAccount =
        service.updateDebtAccount(
            debtAccount.id(),
            new DebtAccountRequest("Student Loan", "New Servicer", money("3400")));

    var incomeSummaryItem =
        service.addIncomeSummaryItem(
            new IncomeSummaryItemRequest("Side Income", "Monthly", money("500")));
    var updatedIncomeSummaryItem =
        service.updateIncomeSummaryItem(
            incomeSummaryItem.id(),
            new IncomeSummaryItemRequest("Side Income", "Quarterly", money("1500")));

    var incomeEvent =
        service.addIncomeEvent(
            new IncomeEventRequest(LocalDate.of(2026, 7, 3), "Bonus", "Bonus", null));
    var updatedIncomeEvent =
        service.updateIncomeEvent(
            incomeEvent.id(),
            new IncomeEventRequest(LocalDate.of(2026, 7, 10), "Paycheck", "Paycheck", 14));

    var importantDate =
        service.addImportantDate(
            new ImportantDateRequest(LocalDate.of(2026, 7, 4), "Independence Day", "Holiday"));
    var updatedImportantDate =
        service.updateImportantDate(
            importantDate.id(),
            new ImportantDateRequest(LocalDate.of(2026, 7, 5), "Observed Holiday", "Holiday"));

    assertThat(updatedAnnualWithdrawal.bill()).isEqualTo("Insurance Renewal");
    assertThat(updatedAnnualWithdrawal.paid()).isTrue();
    assertThat(updatedAssetAccount.categoryKey()).isEqualTo("investments");
    assertThat(updatedAssetAccount.account()).isEqualTo("Brokerage");
    assertThat(updatedDebtAccount.company()).isEqualTo("New Servicer");
    assertThat(updatedIncomeSummaryItem.interval()).isEqualTo("Quarterly");
    assertThat(updatedIncomeEvent.checkNumber()).isEqualTo(14);
    assertThat(updatedImportantDate.event()).isEqualTo("Observed Holiday");

    var snapshotAfterUpdates = service.getSnapshot();
    assertThat(snapshotAfterUpdates.annualWithdrawals())
        .anyMatch((withdrawal) -> withdrawal.id() == updatedAnnualWithdrawal.id());
    assertThat(snapshotAfterUpdates.assetCategories())
        .anyMatch(
            (category) ->
                category.key().equals("investments")
                    && category.accounts().stream()
                        .anyMatch((account) -> account.id() == updatedAssetAccount.id()));
    assertThat(snapshotAfterUpdates.debtAccounts())
        .anyMatch((account) -> account.id() == updatedDebtAccount.id());
    assertThat(snapshotAfterUpdates.incomeSummaryItems())
        .anyMatch((item) -> item.id() == updatedIncomeSummaryItem.id());
    assertThat(snapshotAfterUpdates.incomeEvents())
        .anyMatch((event) -> event.id() == updatedIncomeEvent.id());
    assertThat(snapshotAfterUpdates.importantDates())
        .anyMatch((date) -> date.id() == updatedImportantDate.id());

    service.deleteAnnualWithdrawal(updatedAnnualWithdrawal.id());
    service.deleteAssetAccount(updatedAssetAccount.id());
    service.deleteDebtAccount(updatedDebtAccount.id());
    service.deleteIncomeSummaryItem(updatedIncomeSummaryItem.id());
    service.deleteIncomeEvent(updatedIncomeEvent.id());
    service.deleteImportantDate(updatedImportantDate.id());

    var snapshotAfterDeletes = service.getSnapshot();
    assertThat(snapshotAfterDeletes.annualWithdrawals())
        .noneMatch((withdrawal) -> withdrawal.id() == updatedAnnualWithdrawal.id());
    assertThat(snapshotAfterDeletes.assetCategories())
        .noneMatch(
            (category) ->
                category.accounts().stream()
                    .anyMatch((account) -> account.id() == updatedAssetAccount.id()));
    assertThat(snapshotAfterDeletes.debtAccounts())
        .noneMatch((account) -> account.id() == updatedDebtAccount.id());
    assertThat(snapshotAfterDeletes.incomeSummaryItems())
        .noneMatch((item) -> item.id() == updatedIncomeSummaryItem.id());
    assertThat(snapshotAfterDeletes.incomeEvents())
        .noneMatch((event) -> event.id() == updatedIncomeEvent.id());
    assertThat(snapshotAfterDeletes.importantDates())
        .noneMatch((date) -> date.id() == updatedImportantDate.id());
  }

  @Test
  void updatesPayPeriodAnchor() throws IOException {
    FinancialsService service = new FinancialsService(repository());

    var snapshot =
        service.updatePayPeriod(
            new PayPeriodRequest(LocalDate.of(2026, 6, 12), LocalDate.of(2026, 6, 26)));

    assertThat(snapshot.payPeriodStart()).isNotNull();
    assertThat(snapshot.payPeriodEnd()).isNotNull();
  }

  @Test
  void rollsSavedPayPeriodForwardToCurrentDate() throws IOException {
    Clock clock = Clock.fixed(Instant.parse("2026-06-22T12:00:00Z"), ZoneOffset.UTC);
    FinancialsService service = new FinancialsService(repository(), clock);

    var snapshot = service.getSnapshot();

    assertThat(snapshot.payPeriodStart()).isEqualTo(LocalDate.of(2026, 6, 15));
    assertThat(snapshot.payPeriodEnd()).isEqualTo(LocalDate.of(2026, 6, 29));
    assertThat(snapshot.bills())
        .anyMatch(
            (bill) ->
                bill.bill().equals("Example Savings Transfer")
                    && bill.dueDate().equals(LocalDate.of(2026, 6, 15))
                    && bill.inPayPeriod());
  }

  @Test
  void savesSnapshotInOneBatch() throws IOException {
    FinancialsService service = new FinancialsService(repository());
    long loadedVersion = service.getSnapshot().version();

    var saved =
        service.saveSnapshot(
            new ExpenseSnapshotRequest(
                loadedVersion,
                LocalDate.of(2026, 6, 12),
                LocalDate.of(2026, 6, 26),
                List.of(
                    new ExpenseBillSnapshotRequest(1L, "Rent", 1, money("2600"), "Check", true),
                    new ExpenseBillSnapshotRequest(
                        null, "New Bill", 15, money("25"), "Apple", false)),
                List.of(
                    new AnnualWithdrawalSnapshotRequest(
                        null, "Annual Renewal", 9, 18, money("99"), "Check", false)),
                List.of(
                    new AssetCategorySnapshotRequest(
                        "retirement",
                        "Retirement",
                        List.of(
                            new AssetAccountSnapshotRequest(
                                1L, "401k 10%", "Vanguard", money("110653.42")),
                            new AssetAccountSnapshotRequest(
                                null, "Pension", "Example", money("1000"))))),
                List.of(
                    new DebtAccountSnapshotRequest(null, "Apple", "Apple Card", money("2130.03")),
                    new DebtAccountSnapshotRequest(null, "Line of Credit", "BECU", money("0"))),
                List.of(
                    new IncomeSummaryItemSnapshotRequest(
                        null, "Disposable Income", "Bi-Weekly", money("1901.58"))),
                List.of(
                    new IncomeEventSnapshotRequest(
                        1L, LocalDate.of(2026, 6, 12), "Paycheck", "Paycheck", 12),
                    new IncomeEventSnapshotRequest(
                        null, LocalDate.of(2026, 6, 26), "Paycheck", "Paycheck", 13)),
                List.of(
                    new ImportantDateSnapshotRequest(
                        null, LocalDate.of(2026, 12, 25), "Christmas", "Holiday"))));

    assertThat(saved.version()).isEqualTo(loadedVersion + 1);
    assertThat(saved.bills()).hasSize(2);
    assertThat(saved.bills()).anyMatch((bill) -> bill.bill().equals("Rent"));
    assertThat(saved.annualWithdrawals()).hasSize(1);
    assertThat(saved.totalMonthlyExpenses()).isEqualByComparingTo("2625");
    assertThat(saved.totalAnnualWithdrawals()).isEqualByComparingTo("99");
    assertThat(saved.totalTrackedAssets()).isEqualByComparingTo("111653.42");
    assertThat(saved.totalDebt()).isEqualByComparingTo("2130.03");
    assertThat(saved.netWorth()).isEqualByComparingTo("109523.39");
    assertThat(saved.bills()).anyMatch((bill) -> bill.bill().equals("New Bill") && bill.id() > 0);
    assertThat(saved.assetCategories().getFirst().accounts())
        .anyMatch((account) -> account.account().equals("Pension") && account.id() > 0);
    assertThat(saved.debtAccounts())
        .anyMatch((account) -> account.account().equals("Apple") && account.id() > 0);
    assertThat(saved.incomeSummaryItems())
        .anyMatch((item) -> item.category().equals("Disposable Income") && item.id() > 0);
    assertThat(saved.incomeSummaryItems())
        .anyMatch(
            (item) ->
                item.category().equals("Net Income")
                    && item.interval().equals("Bi-Weekly")
                    && item.id() > 0);
    assertThat(saved.incomeEvents()).hasSize(2);
    assertThat(saved.incomeEvents().getFirst().checksInMonth()).isEqualTo(2);
    assertThat(saved.importantDates())
        .anyMatch((importantDate) -> importantDate.event().equals("Christmas"));
    assertThat(Files.exists(tempDir.resolve("financials.local.json.bak"))).isTrue();
  }

  @Test
  void rejectsStaleSnapshotVersion() throws IOException {
    FinancialsService service = new FinancialsService(repository());
    long staleVersion = service.getSnapshot().version();

    service.addBill(new ExpenseBillRequest("Concurrent Bill", 9, money("10"), "Check", false));

    assertThatThrownBy(
            () ->
                service.saveSnapshot(
                    new ExpenseSnapshotRequest(
                        staleVersion,
                        LocalDate.of(2026, 6, 12),
                        LocalDate.of(2026, 6, 26),
                        List.of(
                            new ExpenseBillSnapshotRequest(
                                1L, "Rent", 1, money("2600"), "Check", true)),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of())))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            (exception) ->
                assertThat(((ResponseStatusException) exception).getStatusCode())
                    .isEqualTo(HttpStatus.CONFLICT));
  }

  private FinancialsRepository repository() throws IOException {
    Path dataPath = tempDir.resolve("financials.local.json");
    Path examplePath = tempDir.resolve("financials.example.json");
    Files.writeString(
        examplePath,
        """
        {
          "version": 1,
          "payPeriodStart": "2026-01-01",
          "payPeriodEnd": "2026-01-15",
          "bills": [
            {
              "id": 1,
              "bill": "Example Rent",
              "dueDay": 1,
              "amount": 1200.0,
              "account": "Checking",
              "paid": false
            },
            {
              "id": 2,
              "bill": "Example Savings Transfer",
              "dueDay": 15,
              "amount": 250.0,
              "account": "Savings",
              "paid": false
            }
          ],
          "annualWithdrawals": [
            {
              "id": 1,
              "bill": "Example Membership",
              "month": 1,
              "day": 15,
              "amount": 99.0,
              "account": "Checking",
              "paid": false
            }
          ],
          "assetAccounts": [
            {
              "id": 1,
              "categoryKey": "retirement",
              "categoryLabel": "Retirement",
              "account": "Example 401k",
              "company": "Example Provider",
              "amount": 10000.0
            },
            {
              "id": 2,
              "categoryKey": "cash-savings",
              "categoryLabel": "Cash & Savings",
              "account": "Emergency Fund",
              "company": "Example Bank",
              "amount": 5000.0
            }
          ],
          "debtAccounts": [
            {
              "id": 1,
              "account": "Example Credit Card",
              "company": "Example Bank",
              "amount": 500.0
            }
          ],
          "incomeSummaryItems": [
            {
              "id": 1,
              "category": "Net Income",
              "interval": "Annual",
              "amount": 75000.0
            }
          ],
          "incomeEvents": [
            {
              "id": 1,
              "date": "2026-01-09",
              "label": "Paycheck",
              "type": "Paycheck",
              "checkNumber": 1
            },
            {
              "id": 2,
              "date": "2026-01-23",
              "label": "Paycheck",
              "type": "Paycheck",
              "checkNumber": 2
            }
          ],
          "importantDates": [
            {
              "id": 1,
              "date": "2026-01-01",
              "event": "New Years",
              "type": "Holiday"
            }
          ]
        }
        """);
    return new FinancialsRepository(
        new JsonFinancialsSnapshotStore(new ObjectMapper(), dataPath, examplePath));
  }

  private BigDecimal money(String value) {
    return new BigDecimal(value);
  }
}

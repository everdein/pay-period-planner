package com.example.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.backend.dto.financials.AnnualWithdrawalSnapshotRequest;
import com.example.backend.dto.financials.AssetAccountSnapshotRequest;
import com.example.backend.dto.financials.AssetCategorySnapshotRequest;
import com.example.backend.dto.financials.DebtAccountSnapshotRequest;
import com.example.backend.dto.financials.ExpenseBillRequest;
import com.example.backend.dto.financials.ExpenseBillSnapshotRequest;
import com.example.backend.dto.financials.ExpenseSnapshotRequest;
import com.example.backend.dto.financials.ImportantDateSnapshotRequest;
import com.example.backend.dto.financials.IncomeEventSnapshotRequest;
import com.example.backend.dto.financials.IncomeSummaryItemSnapshotRequest;
import com.example.backend.dto.financials.PayPeriodRequest;
import com.example.backend.repository.FinancialsRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class FinancialsServiceTests {

  @TempDir private Path tempDir;

  @Test
  void returnsSeededMonthlyExpenseSnapshot() throws IOException {
    FinancialsService service = new FinancialsService(repository());

    var snapshot = service.getSnapshot();

    assertThat(snapshot.totalMonthlyExpenses()).isCloseTo(1450.00, withinCents());
    assertThat(snapshot.totalAnnualWithdrawals()).isCloseTo(99.00, withinCents());
    assertThat(snapshot.totalTrackedAssets()).isCloseTo(15000.00, withinCents());
    assertThat(snapshot.totalDebt()).isCloseTo(500.00, withinCents());
    assertThat(snapshot.netWorth()).isCloseTo(14500.00, withinCents());
    assertThat(snapshot.bills()).isNotEmpty();
    assertThat(snapshot.annualWithdrawals()).hasSize(1);
    assertThat(snapshot.debtAccounts()).hasSize(1);
    assertThat(snapshot.assetCategories()).hasSize(2);
    assertThat(snapshot.incomeSummaryItems()).hasSize(1);
    assertThat(snapshot.incomeEvents()).hasSize(2);
    assertThat(snapshot.importantDates()).hasSize(1);
    assertThat(snapshot.bills().getFirst().bill()).isEqualTo("Example Rent");
  }

  @Test
  void createsUpdatesAndDeletesBill() throws IOException {
    FinancialsService service = new FinancialsService(repository());

    var created = service.addBill(new ExpenseBillRequest("Test Bill", 5, 42.50, "Check", false));
    assertThat(created.bill()).isEqualTo("Test Bill");
    assertThat(created.dueLabel()).isEqualTo("5th");

    var updated =
        service.updateBill(
            created.id(), new ExpenseBillRequest("Updated Bill", 6, 45, "Apple", true));
    assertThat(updated.bill()).isEqualTo("Updated Bill");
    assertThat(updated.amount()).isEqualTo(45);

    service.deleteBill(created.id());
    assertThat(service.getSnapshot().bills()).noneMatch((bill) -> bill.id() == created.id());
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

    var saved =
        service.saveSnapshot(
            new ExpenseSnapshotRequest(
                LocalDate.of(2026, 6, 12),
                LocalDate.of(2026, 6, 26),
                List.of(
                    new ExpenseBillSnapshotRequest(1L, "Rent", 1, 2600, "Check", true),
                    new ExpenseBillSnapshotRequest(null, "New Bill", 15, 25, "Apple", false)),
                List.of(
                    new AnnualWithdrawalSnapshotRequest(
                        null, "Annual Renewal", 9, 18, 99, "Check", false)),
                List.of(
                    new AssetCategorySnapshotRequest(
                        "retirement",
                        "Retirement",
                        List.of(
                            new AssetAccountSnapshotRequest(1L, "401k 10%", "Vanguard", 110653.42),
                            new AssetAccountSnapshotRequest(null, "Pension", "Example", 1000)))),
                List.of(
                    new DebtAccountSnapshotRequest(null, "Apple", "Apple Card", 2130.03),
                    new DebtAccountSnapshotRequest(null, "Line of Credit", "BECU", 0)),
                List.of(
                    new IncomeSummaryItemSnapshotRequest(
                        null, "Disposable Income", "Bi-Weekly", 1901.58)),
                List.of(
                    new IncomeEventSnapshotRequest(
                        1L, LocalDate.of(2026, 6, 12), "Paycheck", "Paycheck", 12),
                    new IncomeEventSnapshotRequest(
                        null, LocalDate.of(2026, 6, 26), "Paycheck", "Paycheck", 13)),
                List.of(
                    new ImportantDateSnapshotRequest(
                        null, LocalDate.of(2026, 12, 25), "Christmas", "Holiday"))));

    assertThat(saved.bills()).hasSize(2);
    assertThat(saved.annualWithdrawals()).hasSize(1);
    assertThat(saved.totalMonthlyExpenses()).isCloseTo(2625, withinCents());
    assertThat(saved.totalAnnualWithdrawals()).isCloseTo(99, withinCents());
    assertThat(saved.totalTrackedAssets()).isCloseTo(111653.42, withinCents());
    assertThat(saved.totalDebt()).isCloseTo(2130.03, withinCents());
    assertThat(saved.netWorth()).isCloseTo(109523.39, withinCents());
    assertThat(saved.bills()).anyMatch((bill) -> bill.bill().equals("New Bill") && bill.id() > 0);
    assertThat(saved.assetCategories().getFirst().accounts())
        .anyMatch((account) -> account.account().equals("Pension") && account.id() > 0);
    assertThat(saved.debtAccounts())
        .anyMatch((account) -> account.account().equals("Apple") && account.id() > 0);
    assertThat(saved.incomeSummaryItems())
        .anyMatch((item) -> item.category().equals("Disposable Income") && item.id() > 0);
    assertThat(saved.incomeEvents()).hasSize(2);
    assertThat(saved.incomeEvents().getFirst().checksInMonth()).isEqualTo(2);
    assertThat(saved.importantDates())
        .anyMatch((importantDate) -> importantDate.event().equals("Christmas"));
  }

  private FinancialsRepository repository() throws IOException {
    Path dataPath = tempDir.resolve("financials.local.json");
    Path examplePath = tempDir.resolve("financials.example.json");
    Files.writeString(
        examplePath,
        """
        {
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
    return new FinancialsRepository(new ObjectMapper(), dataPath, examplePath);
  }

  private org.assertj.core.data.Offset<Double> withinCents() {
    return org.assertj.core.data.Offset.offset(0.01);
  }
}

package com.example.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.backend.domain.financials.AnnualWithdrawal;
import com.example.backend.domain.financials.AssetAccount;
import com.example.backend.domain.financials.DebtAccount;
import com.example.backend.domain.financials.ExpenseBill;
import com.example.backend.domain.financials.FinancialSnapshot;
import com.example.backend.domain.financials.ImportantDate;
import com.example.backend.domain.financials.IncomeEvent;
import com.example.backend.domain.financials.IncomeSummaryItem;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_INTEGRATION_TESTS", matches = "true")
class PostgresFinancialRecordSnapshotAdapterIT {

  private static final String TEST_SCHEMA = "financial_record_snapshot_adapter_test";

  private JdbcTemplate jdbcTemplate;
  private PostgresFinancialRecordSnapshotAdapter adapter;

  @BeforeEach
  void setUp() throws IOException {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setUrl(withCurrentSchema(databaseUrl(), TEST_SCHEMA));
    dataSource.setUsername(requiredEnvironment("DATABASE_USERNAME"));
    dataSource.setPassword(requiredEnvironment("DATABASE_PASSWORD"));

    jdbcTemplate = new JdbcTemplate(dataSource);
    jdbcTemplate.execute("create schema if not exists " + TEST_SCHEMA);
    executeMigration("V2__create_financial_snapshot_document.sql");
    executeMigration("V3__create_financial_record_snapshot_schema.sql");
    executeMigration("V4__add_financial_record_app_id_constraints.sql");

    adapter =
        new PostgresFinancialRecordSnapshotAdapter(
            jdbcTemplate,
            new TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource)));
  }

  @AfterEach
  void tearDown() {
    if (jdbcTemplate != null) {
      jdbcTemplate.execute("drop schema if exists " + TEST_SCHEMA + " cascade");
    }
  }

  @Test
  void savesAndLoadsActiveRelationalSnapshot() {
    assertThat(adapter.loadActiveSnapshot()).isEmpty();

    adapter.replaceActiveSnapshot(snapshot("Water", "31.25", 7));

    FinancialSnapshot loaded = adapter.loadActiveSnapshot().orElseThrow();

    assertThat(loaded.version()).isEqualTo(7);
    assertThat(loaded.payPeriodStart()).isEqualTo(LocalDate.of(2026, 6, 12));
    assertThat(loaded.payPeriodEnd()).isEqualTo(LocalDate.of(2026, 6, 26));
    assertThat(loaded.bills())
        .containsExactly(
            new ExpenseBill(101, "Water", 10, new BigDecimal("31.25"), "Check", false));
    assertThat(loaded.annualWithdrawals())
        .containsExactly(
            new AnnualWithdrawal(
                201, "Domain Renewal", 9, 18, new BigDecimal("99.00"), "Check", false));
    assertThat(loaded.assetAccounts())
        .containsExactly(
            new AssetAccount(
                301,
                "cash-savings",
                "Cash & Savings",
                "Emergency Fund",
                "Credit Union",
                new BigDecimal("5000.00")));
    assertThat(loaded.debtAccounts())
        .containsExactly(
            new DebtAccount(401, "Credit Card", "Example Bank", new BigDecimal("250.00")));
    assertThat(loaded.incomeSummaryItems())
        .containsExactly(
            new IncomeSummaryItem(501, "Net Income", "Bi-Weekly", new BigDecimal("1901.58")));
    assertThat(loaded.incomeEvents())
        .containsExactly(
            new IncomeEvent(601, LocalDate.of(2026, 6, 12), "Paycheck", "Paycheck", 12));
    assertThat(loaded.importantDates())
        .containsExactly(
            new ImportantDate(701, LocalDate.of(2026, 12, 25), "Christmas", "Holiday"));
  }

  @Test
  void replacingSnapshotKeepsOneActiveRelationalSnapshot() {
    adapter.replaceActiveSnapshot(snapshot("Water", "31.25", 7));
    adapter.replaceActiveSnapshot(snapshot("Electricity", "42.50", 8));

    FinancialSnapshot loaded = adapter.loadActiveSnapshot().orElseThrow();

    assertThat(loaded.version()).isEqualTo(8);
    assertThat(loaded.bills()).extracting(ExpenseBill::bill).containsExactly("Electricity");
    assertThat(countRows("financial_record_snapshot")).isEqualTo(2);
    assertThat(countRows("financial_record_snapshot where active = true")).isEqualTo(1);
    assertThat(countRows("financial_record_monthly_bill")).isEqualTo(2);
  }

  @Test
  void createsReadsUpdatesAndDeletesGranularRecordsInActiveSnapshot() {
    adapter.replaceActiveSnapshot(emptySnapshot());

    ExpenseBill createdBill =
        adapter.createBill(
            new ExpenseBill(0, "Internet", 12, new BigDecimal("80.00"), "Check", false));
    AnnualWithdrawal createdAnnualWithdrawal =
        adapter.createAnnualWithdrawal(
            new AnnualWithdrawal(0, "Insurance", 4, 15, new BigDecimal("120.00"), "Check", false));
    AssetAccount createdAssetAccount =
        adapter.createAssetAccount(
            new AssetAccount(
                0,
                "cash-savings",
                "Cash & Savings",
                "Vacation",
                "Credit Union",
                new BigDecimal("900.00")));
    DebtAccount createdDebtAccount =
        adapter.createDebtAccount(
            new DebtAccount(0, "Student Loan", "Loan Servicer", new BigDecimal("3500.00")));
    IncomeSummaryItem createdIncomeSummaryItem =
        adapter.createIncomeSummaryItem(
            new IncomeSummaryItem(0, "Side Income", "Monthly", new BigDecimal("500.00")));
    IncomeEvent createdIncomeEvent =
        adapter.createIncomeEvent(
            new IncomeEvent(0, LocalDate.of(2026, 7, 3), "Bonus", "Bonus", null));
    ImportantDate createdImportantDate =
        adapter.createImportantDate(
            new ImportantDate(0, LocalDate.of(2026, 7, 4), "Independence Day", "Holiday"));

    assertThat(adapter.findBill(createdBill.id())).contains(createdBill);
    assertThat(adapter.findAnnualWithdrawal(createdAnnualWithdrawal.id()))
        .contains(createdAnnualWithdrawal);
    assertThat(adapter.findAssetAccount(createdAssetAccount.id())).contains(createdAssetAccount);
    assertThat(adapter.findDebtAccount(createdDebtAccount.id())).contains(createdDebtAccount);
    assertThat(adapter.findIncomeSummaryItem(createdIncomeSummaryItem.id()))
        .contains(createdIncomeSummaryItem);
    assertThat(adapter.findIncomeEvent(createdIncomeEvent.id())).contains(createdIncomeEvent);
    assertThat(adapter.findImportantDate(createdImportantDate.id())).contains(createdImportantDate);

    ExpenseBill updatedBill =
        adapter
            .updateBill(
                createdBill.id(),
                new ExpenseBill(0, "Internet Plus", 13, new BigDecimal("90.00"), "Apple", true))
            .orElseThrow();
    AnnualWithdrawal updatedAnnualWithdrawal =
        adapter
            .updateAnnualWithdrawal(
                createdAnnualWithdrawal.id(),
                new AnnualWithdrawal(
                    0, "Insurance Renewal", 5, 16, new BigDecimal("130.00"), "Apple", true))
            .orElseThrow();
    AssetAccount updatedAssetAccount =
        adapter
            .updateAssetAccount(
                createdAssetAccount.id(),
                new AssetAccount(
                    0,
                    "investments",
                    "Investments",
                    "Brokerage",
                    "Example Broker",
                    new BigDecimal("1250.00")))
            .orElseThrow();
    DebtAccount updatedDebtAccount =
        adapter
            .updateDebtAccount(
                createdDebtAccount.id(),
                new DebtAccount(0, "Student Loan", "New Servicer", new BigDecimal("3400.00")))
            .orElseThrow();
    IncomeSummaryItem updatedIncomeSummaryItem =
        adapter
            .updateIncomeSummaryItem(
                createdIncomeSummaryItem.id(),
                new IncomeSummaryItem(0, "Side Income", "Quarterly", new BigDecimal("1500.00")))
            .orElseThrow();
    IncomeEvent updatedIncomeEvent =
        adapter
            .updateIncomeEvent(
                createdIncomeEvent.id(),
                new IncomeEvent(0, LocalDate.of(2026, 7, 10), "Paycheck", "Paycheck", 14))
            .orElseThrow();
    ImportantDate updatedImportantDate =
        adapter
            .updateImportantDate(
                createdImportantDate.id(),
                new ImportantDate(0, LocalDate.of(2026, 7, 5), "Observed Holiday", "Holiday"))
            .orElseThrow();

    FinancialSnapshot updatedSnapshot = adapter.loadActiveSnapshot().orElseThrow();

    assertThat(updatedSnapshot.version()).isEqualTo(15);
    assertThat(updatedSnapshot.bills()).containsExactly(updatedBill);
    assertThat(updatedSnapshot.annualWithdrawals()).containsExactly(updatedAnnualWithdrawal);
    assertThat(updatedSnapshot.assetAccounts()).containsExactly(updatedAssetAccount);
    assertThat(updatedSnapshot.debtAccounts()).containsExactly(updatedDebtAccount);
    assertThat(updatedSnapshot.incomeSummaryItems()).containsExactly(updatedIncomeSummaryItem);
    assertThat(updatedSnapshot.incomeEvents()).containsExactly(updatedIncomeEvent);
    assertThat(updatedSnapshot.importantDates()).containsExactly(updatedImportantDate);

    assertThat(adapter.updateBill(999, updatedBill)).isEmpty();
    assertThat(adapter.deleteBill(999)).isFalse();

    assertThat(adapter.deleteBill(createdBill.id())).isTrue();
    assertThat(adapter.deleteAnnualWithdrawal(createdAnnualWithdrawal.id())).isTrue();
    assertThat(adapter.deleteAssetAccount(createdAssetAccount.id())).isTrue();
    assertThat(adapter.deleteDebtAccount(createdDebtAccount.id())).isTrue();
    assertThat(adapter.deleteIncomeSummaryItem(createdIncomeSummaryItem.id())).isTrue();
    assertThat(adapter.deleteIncomeEvent(createdIncomeEvent.id())).isTrue();
    assertThat(adapter.deleteImportantDate(createdImportantDate.id())).isTrue();

    FinancialSnapshot emptyAfterDeletes = adapter.loadActiveSnapshot().orElseThrow();

    assertThat(emptyAfterDeletes.version()).isEqualTo(22);
    assertThat(emptyAfterDeletes.bills()).isEmpty();
    assertThat(emptyAfterDeletes.annualWithdrawals()).isEmpty();
    assertThat(emptyAfterDeletes.assetAccounts()).isEmpty();
    assertThat(emptyAfterDeletes.debtAccounts()).isEmpty();
    assertThat(emptyAfterDeletes.incomeSummaryItems()).isEmpty();
    assertThat(emptyAfterDeletes.incomeEvents()).isEmpty();
    assertThat(emptyAfterDeletes.importantDates()).isEmpty();
  }

  private FinancialSnapshot snapshot(String billName, String amount, long version) {
    return new FinancialSnapshot(
        version,
        LocalDate.of(2026, 6, 12),
        LocalDate.of(2026, 6, 26),
        List.of(new ExpenseBill(101, billName, 10, new BigDecimal(amount), "Check", false)),
        List.of(
            new AnnualWithdrawal(
                201, "Domain Renewal", 9, 18, new BigDecimal("99.00"), "Check", false)),
        List.of(
            new AssetAccount(
                301,
                "cash-savings",
                "Cash & Savings",
                "Emergency Fund",
                "Credit Union",
                new BigDecimal("5000.00"))),
        List.of(new DebtAccount(401, "Credit Card", "Example Bank", new BigDecimal("250.00"))),
        List.of(new IncomeSummaryItem(501, "Net Income", "Bi-Weekly", new BigDecimal("1901.58"))),
        List.of(new IncomeEvent(601, LocalDate.of(2026, 6, 12), "Paycheck", "Paycheck", 12)),
        List.of(new ImportantDate(701, LocalDate.of(2026, 12, 25), "Christmas", "Holiday")));
  }

  private FinancialSnapshot emptySnapshot() {
    return new FinancialSnapshot(
        1,
        LocalDate.of(2026, 6, 12),
        LocalDate.of(2026, 6, 26),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }

  private void executeMigration(String migrationFile) throws IOException {
    String migration =
        Files.readString(Path.of("src/main/resources/db/migration").resolve(migrationFile));
    for (String statement : migration.split(";")) {
      if (!statement.isBlank()) {
        jdbcTemplate.execute(statement);
      }
    }
  }

  private int countRows(String tableExpression) {
    return jdbcTemplate.queryForObject("select count(*) from " + tableExpression, Integer.class);
  }

  private String databaseUrl() {
    return environmentOrDefault("DATABASE_URL", "jdbc:postgresql://localhost:5432/financial_app");
  }

  private String withCurrentSchema(String url, String schema) {
    String separator = url.contains("?") ? "&" : "?";
    return url + separator + "currentSchema=" + schema;
  }

  private String environmentOrDefault(String name, String defaultValue) {
    String value = System.getenv(name);
    return value == null || value.isBlank() ? defaultValue : value;
  }

  private String requiredEnvironment(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(name + " must be set for PostgreSQL integration tests");
    }
    return value;
  }
}

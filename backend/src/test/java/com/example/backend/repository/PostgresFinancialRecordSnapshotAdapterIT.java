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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.flywaydb.core.Flyway;
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
  private long workspaceId;

  @BeforeEach
  void setUp() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setUrl(withCurrentSchema(databaseUrl(), TEST_SCHEMA));
    dataSource.setUsername(requiredEnvironment("DATABASE_USERNAME"));
    dataSource.setPassword(requiredEnvironment("DATABASE_PASSWORD"));

    Flyway.configure()
        .dataSource(dataSource)
        .defaultSchema(TEST_SCHEMA)
        .schemas(TEST_SCHEMA)
        .locations("classpath:db/migration")
        .validateMigrationNaming(true)
        .load()
        .migrate();

    jdbcTemplate = new JdbcTemplate(dataSource);
    workspaceId = createWorkspace("owner@example.com", "Primary Workspace");

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
    assertThat(adapter.loadActiveSnapshot(workspaceId)).isEmpty();

    adapter.replaceActiveSnapshot(workspaceId, snapshot("Water", "31.25", 7));

    FinancialSnapshot loaded = adapter.loadActiveSnapshot(workspaceId).orElseThrow();

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
    adapter.replaceActiveSnapshot(workspaceId, snapshot("Water", "31.25", 7));
    adapter.replaceActiveSnapshot(workspaceId, snapshot("Electricity", "42.50", 8));

    FinancialSnapshot loaded = adapter.loadActiveSnapshot(workspaceId).orElseThrow();

    assertThat(loaded.version()).isEqualTo(8);
    assertThat(loaded.bills()).extracting(ExpenseBill::bill).containsExactly("Electricity");
    assertThat(countRows("financial_record_snapshot")).isEqualTo(2);
    assertThat(countRows("financial_record_snapshot where active = true")).isEqualTo(1);
    assertThat(countRows("financial_record_monthly_bill")).isEqualTo(2);
  }

  @Test
  void createsReadsUpdatesAndDeletesGranularRecordsInActiveSnapshot() {
    adapter.replaceActiveSnapshot(workspaceId, emptySnapshot());

    ExpenseBill createdBill =
        adapter.createBill(
            workspaceId,
            new ExpenseBill(0, "Internet", 12, new BigDecimal("80.00"), "Check", false));
    AnnualWithdrawal createdAnnualWithdrawal =
        adapter.createAnnualWithdrawal(
            workspaceId,
            new AnnualWithdrawal(0, "Insurance", 4, 15, new BigDecimal("120.00"), "Check", false));
    AssetAccount createdAssetAccount =
        adapter.createAssetAccount(
            workspaceId,
            new AssetAccount(
                0,
                "cash-savings",
                "Cash & Savings",
                "Vacation",
                "Credit Union",
                new BigDecimal("900.00")));
    DebtAccount createdDebtAccount =
        adapter.createDebtAccount(
            workspaceId,
            new DebtAccount(0, "Student Loan", "Loan Servicer", new BigDecimal("3500.00")));
    IncomeSummaryItem createdIncomeSummaryItem =
        adapter.createIncomeSummaryItem(
            workspaceId,
            new IncomeSummaryItem(0, "Side Income", "Monthly", new BigDecimal("500.00")));
    IncomeEvent createdIncomeEvent =
        adapter.createIncomeEvent(
            workspaceId, new IncomeEvent(0, LocalDate.of(2026, 7, 3), "Bonus", "Bonus", null));
    ImportantDate createdImportantDate =
        adapter.createImportantDate(
            workspaceId,
            new ImportantDate(0, LocalDate.of(2026, 7, 4), "Independence Day", "Holiday"));

    assertThat(adapter.findBill(workspaceId, createdBill.id())).contains(createdBill);
    assertThat(adapter.findAnnualWithdrawal(workspaceId, createdAnnualWithdrawal.id()))
        .contains(createdAnnualWithdrawal);
    assertThat(adapter.findAssetAccount(workspaceId, createdAssetAccount.id()))
        .contains(createdAssetAccount);
    assertThat(adapter.findDebtAccount(workspaceId, createdDebtAccount.id()))
        .contains(createdDebtAccount);
    assertThat(adapter.findIncomeSummaryItem(workspaceId, createdIncomeSummaryItem.id()))
        .contains(createdIncomeSummaryItem);
    assertThat(adapter.findIncomeEvent(workspaceId, createdIncomeEvent.id()))
        .contains(createdIncomeEvent);
    assertThat(adapter.findImportantDate(workspaceId, createdImportantDate.id()))
        .contains(createdImportantDate);

    ExpenseBill updatedBill =
        adapter
            .updateBill(
                workspaceId,
                createdBill.id(),
                new ExpenseBill(0, "Internet Plus", 13, new BigDecimal("90.00"), "Apple", true))
            .orElseThrow();
    AnnualWithdrawal updatedAnnualWithdrawal =
        adapter
            .updateAnnualWithdrawal(
                workspaceId,
                createdAnnualWithdrawal.id(),
                new AnnualWithdrawal(
                    0, "Insurance Renewal", 5, 16, new BigDecimal("130.00"), "Apple", true))
            .orElseThrow();
    AssetAccount updatedAssetAccount =
        adapter
            .updateAssetAccount(
                workspaceId,
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
                workspaceId,
                createdDebtAccount.id(),
                new DebtAccount(0, "Student Loan", "New Servicer", new BigDecimal("3400.00")))
            .orElseThrow();
    IncomeSummaryItem updatedIncomeSummaryItem =
        adapter
            .updateIncomeSummaryItem(
                workspaceId,
                createdIncomeSummaryItem.id(),
                new IncomeSummaryItem(0, "Side Income", "Quarterly", new BigDecimal("1500.00")))
            .orElseThrow();
    IncomeEvent updatedIncomeEvent =
        adapter
            .updateIncomeEvent(
                workspaceId,
                createdIncomeEvent.id(),
                new IncomeEvent(0, LocalDate.of(2026, 7, 10), "Paycheck", "Paycheck", 14))
            .orElseThrow();
    ImportantDate updatedImportantDate =
        adapter
            .updateImportantDate(
                workspaceId,
                createdImportantDate.id(),
                new ImportantDate(0, LocalDate.of(2026, 7, 5), "Observed Holiday", "Holiday"))
            .orElseThrow();

    FinancialSnapshot updatedSnapshot = adapter.loadActiveSnapshot(workspaceId).orElseThrow();

    assertThat(updatedSnapshot.version()).isEqualTo(15);
    assertThat(updatedSnapshot.bills()).containsExactly(updatedBill);
    assertThat(updatedSnapshot.annualWithdrawals()).containsExactly(updatedAnnualWithdrawal);
    assertThat(updatedSnapshot.assetAccounts()).containsExactly(updatedAssetAccount);
    assertThat(updatedSnapshot.debtAccounts()).containsExactly(updatedDebtAccount);
    assertThat(updatedSnapshot.incomeSummaryItems()).containsExactly(updatedIncomeSummaryItem);
    assertThat(updatedSnapshot.incomeEvents()).containsExactly(updatedIncomeEvent);
    assertThat(updatedSnapshot.importantDates()).containsExactly(updatedImportantDate);

    assertThat(adapter.updateBill(workspaceId, 999, updatedBill)).isEmpty();
    assertThat(adapter.deleteBill(workspaceId, 999)).isFalse();

    assertThat(adapter.deleteBill(workspaceId, createdBill.id())).isTrue();
    assertThat(adapter.deleteAnnualWithdrawal(workspaceId, createdAnnualWithdrawal.id())).isTrue();
    assertThat(adapter.deleteAssetAccount(workspaceId, createdAssetAccount.id())).isTrue();
    assertThat(adapter.deleteDebtAccount(workspaceId, createdDebtAccount.id())).isTrue();
    assertThat(adapter.deleteIncomeSummaryItem(workspaceId, createdIncomeSummaryItem.id()))
        .isTrue();
    assertThat(adapter.deleteIncomeEvent(workspaceId, createdIncomeEvent.id())).isTrue();
    assertThat(adapter.deleteImportantDate(workspaceId, createdImportantDate.id())).isTrue();

    FinancialSnapshot emptyAfterDeletes = adapter.loadActiveSnapshot(workspaceId).orElseThrow();

    assertThat(emptyAfterDeletes.version()).isEqualTo(22);
    assertThat(emptyAfterDeletes.bills()).isEmpty();
    assertThat(emptyAfterDeletes.annualWithdrawals()).isEmpty();
    assertThat(emptyAfterDeletes.assetAccounts()).isEmpty();
    assertThat(emptyAfterDeletes.debtAccounts()).isEmpty();
    assertThat(emptyAfterDeletes.incomeSummaryItems()).isEmpty();
    assertThat(emptyAfterDeletes.incomeEvents()).isEmpty();
    assertThat(emptyAfterDeletes.importantDates()).isEmpty();
  }

  @Test
  void isolatesSnapshotAndGranularOperationsByWorkspace() {
    long otherWorkspaceId = createWorkspace("other@example.com", "Other Workspace");
    FinancialSnapshot primarySnapshot = isolationSnapshot("Primary", 7);
    FinancialSnapshot otherSnapshot = isolationSnapshot("Other", 9);
    FinancialSnapshot changedSnapshot = isolationSnapshot("Changed", 10);

    adapter.replaceActiveSnapshot(workspaceId, primarySnapshot);
    adapter.replaceActiveSnapshot(otherWorkspaceId, otherSnapshot);

    assertThat(countRows("financial_record_snapshot where active = true")).isEqualTo(2);
    assertThat(adapter.loadActiveSnapshot(workspaceId)).contains(primarySnapshot);
    assertThat(adapter.loadActiveSnapshot(otherWorkspaceId)).contains(otherSnapshot);

    assertThat(adapter.updateBill(workspaceId, 1, changedSnapshot.bills().get(0)))
        .contains(changedSnapshot.bills().get(0));
    assertThat(
            adapter.updateAnnualWithdrawal(
                workspaceId, 1, changedSnapshot.annualWithdrawals().get(0)))
        .contains(changedSnapshot.annualWithdrawals().get(0));
    assertThat(adapter.updateAssetAccount(workspaceId, 1, changedSnapshot.assetAccounts().get(0)))
        .contains(changedSnapshot.assetAccounts().get(0));
    assertThat(adapter.updateDebtAccount(workspaceId, 1, changedSnapshot.debtAccounts().get(0)))
        .contains(changedSnapshot.debtAccounts().get(0));
    assertThat(
            adapter.updateIncomeSummaryItem(
                workspaceId, 1, changedSnapshot.incomeSummaryItems().get(0)))
        .contains(changedSnapshot.incomeSummaryItems().get(0));
    assertThat(adapter.updateIncomeEvent(workspaceId, 1, changedSnapshot.incomeEvents().get(0)))
        .contains(changedSnapshot.incomeEvents().get(0));
    assertThat(adapter.updateImportantDate(workspaceId, 1, changedSnapshot.importantDates().get(0)))
        .contains(changedSnapshot.importantDates().get(0));

    assertThat(adapter.findBill(workspaceId, 1)).contains(changedSnapshot.bills().get(0));
    assertThat(adapter.findAnnualWithdrawal(workspaceId, 1))
        .contains(changedSnapshot.annualWithdrawals().get(0));
    assertThat(adapter.findAssetAccount(workspaceId, 1))
        .contains(changedSnapshot.assetAccounts().get(0));
    assertThat(adapter.findDebtAccount(workspaceId, 1))
        .contains(changedSnapshot.debtAccounts().get(0));
    assertThat(adapter.findIncomeSummaryItem(workspaceId, 1))
        .contains(changedSnapshot.incomeSummaryItems().get(0));
    assertThat(adapter.findIncomeEvent(workspaceId, 1))
        .contains(changedSnapshot.incomeEvents().get(0));
    assertThat(adapter.findImportantDate(workspaceId, 1))
        .contains(changedSnapshot.importantDates().get(0));
    assertThat(adapter.loadActiveSnapshot(otherWorkspaceId)).contains(otherSnapshot);

    assertThat(adapter.deleteBill(workspaceId, 1)).isTrue();
    assertThat(adapter.deleteAnnualWithdrawal(workspaceId, 1)).isTrue();
    assertThat(adapter.deleteAssetAccount(workspaceId, 1)).isTrue();
    assertThat(adapter.deleteDebtAccount(workspaceId, 1)).isTrue();
    assertThat(adapter.deleteIncomeSummaryItem(workspaceId, 1)).isTrue();
    assertThat(adapter.deleteIncomeEvent(workspaceId, 1)).isTrue();
    assertThat(adapter.deleteImportantDate(workspaceId, 1)).isTrue();

    FinancialSnapshot primaryAfterDeletes = adapter.loadActiveSnapshot(workspaceId).orElseThrow();
    assertThat(primaryAfterDeletes.bills()).isEmpty();
    assertThat(primaryAfterDeletes.annualWithdrawals()).isEmpty();
    assertThat(primaryAfterDeletes.assetAccounts()).isEmpty();
    assertThat(primaryAfterDeletes.debtAccounts()).isEmpty();
    assertThat(primaryAfterDeletes.incomeSummaryItems()).isEmpty();
    assertThat(primaryAfterDeletes.incomeEvents()).isEmpty();
    assertThat(primaryAfterDeletes.importantDates()).isEmpty();
    assertThat(adapter.loadActiveSnapshot(otherWorkspaceId)).contains(otherSnapshot);
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

  private FinancialSnapshot isolationSnapshot(String prefix, long version) {
    return new FinancialSnapshot(
        version,
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 14),
        List.of(
            new ExpenseBill(
                1, prefix + " Bill", 10, new BigDecimal("31.25"), prefix + " Cash", false)),
        List.of(
            new AnnualWithdrawal(
                1, prefix + " Annual", 9, 18, new BigDecimal("99.00"), prefix + " Cash", false)),
        List.of(
            new AssetAccount(
                1,
                "cash-savings",
                "Cash & Savings",
                prefix + " Asset",
                prefix + " Bank",
                new BigDecimal("5000.00"))),
        List.of(new DebtAccount(1, prefix + " Debt", prefix + " Lender", new BigDecimal("250.00"))),
        List.of(
            new IncomeSummaryItem(1, prefix + " Income", "Bi-Weekly", new BigDecimal("1901.58"))),
        List.of(new IncomeEvent(1, LocalDate.of(2026, 7, 3), prefix + " Paycheck", "Paycheck", 12)),
        List.of(new ImportantDate(1, LocalDate.of(2026, 12, 25), prefix + " Holiday", "Holiday")));
  }

  private long createWorkspace(String email, String name) {
    Long userId =
        jdbcTemplate.queryForObject(
            """
            insert into application_user (email, password_hash, display_name)
            values (?, ?, ?)
            returning id
            """,
            Long.class,
            email,
            "$synthetic$password$hash",
            name + " Owner");
    Long createdWorkspaceId =
        jdbcTemplate.queryForObject(
            """
            insert into workspace (name, created_by_user_id)
            values (?, ?)
            returning id
            """,
            Long.class,
            name,
            userId);
    jdbcTemplate.update(
        "insert into workspace_membership (workspace_id, user_id, role) values (?, ?, 'owner')",
        createdWorkspaceId,
        userId);
    return createdWorkspaceId;
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

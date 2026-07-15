package com.example.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.example.backend.domain.financials.PayCadence;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DuplicateKeyException;
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
    assertThat(loaded.planningSettings())
        .isEqualTo(new FinancialPlanningSettings(PayCadence.SEMIMONTHLY, "America/New_York"));
    assertThat(loaded.projectionRoles()).isEqualTo(new FinancialProjectionRoles(101, 301, 501));
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
  void refusesToCreateASecondInitialSnapshotForOneWorkspace() {
    adapter.createInitialSnapshot(workspaceId, emptySnapshot());

    assertThatThrownBy(() -> adapter.createInitialSnapshot(workspaceId, emptySnapshot()))
        .isInstanceOf(DuplicateKeyException.class)
        .hasMessageContaining("active financial snapshot");
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
    assertThat(countRows("financial_record_projection_role")).isEqualTo(6);
  }

  @Test
  void isolatesAggregateReplacementsByWorkspace() {
    long otherWorkspaceId = createWorkspace("other@example.com", "Other Workspace");
    FinancialSnapshot primarySnapshot = isolationSnapshot("Primary", 7);
    FinancialSnapshot otherSnapshot = isolationSnapshot("Other", 9);
    FinancialSnapshot changedSnapshot = isolationSnapshot("Changed", 10);

    adapter.replaceActiveSnapshot(workspaceId, primarySnapshot);
    adapter.replaceActiveSnapshot(otherWorkspaceId, otherSnapshot);

    assertThat(countRows("financial_record_snapshot where active = true")).isEqualTo(2);
    assertThat(adapter.loadActiveSnapshot(workspaceId)).contains(primarySnapshot);
    assertThat(adapter.loadActiveSnapshot(otherWorkspaceId)).contains(otherSnapshot);

    adapter.replaceActiveSnapshot(workspaceId, changedSnapshot);

    assertThat(adapter.loadActiveSnapshot(workspaceId)).contains(changedSnapshot);
    assertThat(adapter.loadActiveSnapshot(otherWorkspaceId)).contains(otherSnapshot);
    assertThat(countRows("financial_record_snapshot where active = true")).isEqualTo(2);
  }

  private FinancialSnapshot snapshot(String billName, String amount, long version) {
    return new FinancialSnapshot(
        version,
        LocalDate.of(2026, 6, 12),
        LocalDate.of(2026, 6, 26),
        new FinancialPlanningSettings(PayCadence.SEMIMONTHLY, "America/New_York"),
        new FinancialProjectionRoles(101, 301, 501),
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
        FinancialPlanningSettings.legacyDefaults(),
        new FinancialProjectionRoles(1, 1, 1),
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

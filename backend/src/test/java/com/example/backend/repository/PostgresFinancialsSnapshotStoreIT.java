package com.example.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.backend.domain.financials.ExpenseBill;
import com.example.backend.domain.financials.FinancialSnapshot;
import com.example.backend.service.WorkspaceFinancialSnapshotNotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
class PostgresFinancialsSnapshotStoreIT {

  private static final String TEST_SCHEMA = "financial_workspace_store_test";

  private JdbcTemplate jdbcTemplate;
  private PostgresFinancialRecordSnapshotAdapter adapter;
  private PostgresFinancialsSnapshotStore store;
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
    WorkspaceOwner owner = createWorkspace("runtime-owner@example.com", "Runtime Workspace");
    workspaceId = owner.workspaceId();

    adapter =
        new PostgresFinancialRecordSnapshotAdapter(
            jdbcTemplate,
            new TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource)));
    store = new PostgresFinancialsSnapshotStore(adapter, () -> workspaceId);
  }

  @AfterEach
  void tearDown() {
    if (jdbcTemplate != null) {
      jdbcTemplate.execute("drop schema if exists " + TEST_SCHEMA + " cascade");
    }
  }

  @Test
  void requiresAnExplicitlyMigratedWorkspaceSnapshot() {
    assertThatThrownBy(store::loadCurrentSnapshot)
        .isInstanceOf(WorkspaceFinancialSnapshotNotFoundException.class)
        .hasMessageContaining(Long.toString(workspaceId));
  }

  @Test
  void replacesTheActiveRelationalSnapshotAndPreservesAuditHistory() {
    adapter.createInitialSnapshot(workspaceId, emptySnapshot(7));
    Clock clock = Clock.fixed(Instant.parse("2026-07-14T12:00:00Z"), ZoneOffset.UTC);
    FinancialsRepository repository = new FinancialsRepository(store, clock);

    FinancialSnapshot current = repository.currentSnapshot();
    repository.replaceSnapshot(
        current.version(),
        new FinancialSnapshot(
            current.version(),
            current.payPeriodStart(),
            current.payPeriodEnd(),
            List.of(new ExpenseBill(0, "Water", 10, new BigDecimal("31.25"), "Check", false)),
            current.annualWithdrawals(),
            current.assetAccounts(),
            current.debtAccounts(),
            current.incomeSummaryItems(),
            current.incomeEvents(),
            current.importantDates()));

    FinancialSnapshot afterFirstReplacement = repository.currentSnapshot();
    repository.replaceSnapshot(
        afterFirstReplacement.version(),
        new FinancialSnapshot(
            afterFirstReplacement.version(),
            afterFirstReplacement.payPeriodStart(),
            afterFirstReplacement.payPeriodEnd(),
            List.of(new ExpenseBill(1, "Water", 10, new BigDecimal("32.50"), "Check", true)),
            afterFirstReplacement.annualWithdrawals(),
            afterFirstReplacement.assetAccounts(),
            afterFirstReplacement.debtAccounts(),
            afterFirstReplacement.incomeSummaryItems(),
            afterFirstReplacement.incomeEvents(),
            afterFirstReplacement.importantDates()));

    FinancialSnapshot loaded = store.loadCurrentSnapshot();
    assertThat(loaded.bills()).extracting(ExpenseBill::bill).containsExactly("Water");
    assertThat(loaded.version()).isEqualTo(9);
    assertThat(loaded.bills().getFirst().amount()).isEqualByComparingTo("32.50");
    assertThat(store.loadAuditHistory(1))
        .singleElement()
        .satisfies(
            (event) -> {
              assertThat(event.id()).isEqualTo(2);
              assertThat(event.versionBefore()).isEqualTo(8);
              assertThat(event.versionAfter()).isEqualTo(9);
            });
    assertThat(store.loadAuditHistory(10))
        .extracting((event) -> event.versionAfter())
        .containsExactly(9L, 8L);
    assertThat(countRows("financial_record_snapshot where workspace_id = " + workspaceId))
        .isEqualTo(3);
    assertThat(
            countRows(
                "financial_record_snapshot where workspace_id = " + workspaceId + " and active"))
        .isEqualTo(1);
    assertThat(countRows("financial_record_audit_event")).isEqualTo(2);
  }

  @Test
  void rejectsAStaleRelationalReplacement() {
    adapter.createInitialSnapshot(workspaceId, emptySnapshot(3));
    FinancialsRepository staleRepository =
        new FinancialsRepository(
            store, Clock.fixed(Instant.parse("2026-07-14T12:00:00Z"), ZoneOffset.UTC));
    FinancialSnapshot stale = staleRepository.currentSnapshot();
    adapter.replaceActiveSnapshot(workspaceId, emptySnapshot(4));

    assertThatThrownBy(() -> staleRepository.replaceSnapshot(stale.version(), stale))
        .isInstanceOf(SnapshotVersionConflictException.class)
        .hasMessageContaining("current version is 4");
  }

  private FinancialSnapshot emptySnapshot(long version) {
    return new FinancialSnapshot(
        version,
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 14),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }

  private WorkspaceOwner createWorkspace(String email, String name) {
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
    return new WorkspaceOwner(userId, email, createdWorkspaceId, name);
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

  private record WorkspaceOwner(
      long userId, String email, long workspaceId, String workspaceName) {}
}

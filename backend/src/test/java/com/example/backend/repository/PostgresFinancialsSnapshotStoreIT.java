package com.example.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.backend.domain.financials.ExpenseBill;
import com.example.backend.domain.financials.FinancialSnapshot;
import com.example.backend.domain.identity.AuthenticatedSession;
import com.example.backend.domain.identity.WorkspaceAccess;
import com.example.backend.service.AuthenticatedWorkspaceResolver;
import com.example.backend.service.WorkspaceFinancialSnapshotNotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
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
    authenticate(owner);

    adapter =
        new PostgresFinancialRecordSnapshotAdapter(
            jdbcTemplate,
            new TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource)));
    store =
        new PostgresFinancialsSnapshotStore(
            adapter, new AuthenticatedWorkspaceResolver(), new MockHttpServletRequest());
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    if (jdbcTemplate != null) {
      jdbcTemplate.execute("drop schema if exists " + TEST_SCHEMA + " cascade");
    }
  }

  @Test
  void requiresAnExplicitlyMigratedWorkspaceSnapshot() {
    assertThatThrownBy(store::load)
        .isInstanceOf(WorkspaceFinancialSnapshotNotFoundException.class)
        .hasMessageContaining(Long.toString(workspaceId));
  }

  @Test
  void replacesTheActiveRelationalSnapshotAndPreservesAuditHistory() {
    adapter.createInitialSnapshot(workspaceId, emptySnapshot(7));
    Clock clock = Clock.fixed(Instant.parse("2026-07-14T12:00:00Z"), ZoneOffset.UTC);
    FinancialsRepository repository = new FinancialsRepository(store, clock);

    repository.addBill(new ExpenseBill(0, "Water", 10, new BigDecimal("31.25"), "Check", false));

    FinancialsData loaded = store.load();
    assertThat(loaded.version()).isEqualTo(8);
    assertThat(loaded.bills()).extracting(ExpenseBill::bill).containsExactly("Water");
    assertThat(loaded.auditEvents()).hasSize(1);
    assertThat(loaded.auditEvents().getFirst().versionBefore()).isEqualTo(7);
    assertThat(loaded.auditEvents().getFirst().versionAfter()).isEqualTo(8);
    assertThat(countRows("financial_record_snapshot where workspace_id = " + workspaceId))
        .isEqualTo(2);
    assertThat(
            countRows(
                "financial_record_snapshot where workspace_id = " + workspaceId + " and active"))
        .isEqualTo(1);
    assertThat(countRows("financial_record_audit_event")).isEqualTo(1);
  }

  @Test
  void rejectsAStaleRelationalReplacement() {
    adapter.createInitialSnapshot(workspaceId, emptySnapshot(3));
    FinancialsData stale = store.load();
    adapter.replaceActiveSnapshot(workspaceId, emptySnapshot(4));

    assertThatThrownBy(() -> store.save(stale.withVersion(4)))
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

  private void authenticate(WorkspaceOwner owner) {
    AuthenticatedSession session =
        new AuthenticatedSession(
            UUID.randomUUID(),
            owner.userId(),
            owner.email(),
            "Runtime Owner",
            Instant.parse("2026-08-14T12:00:00Z"),
            List.of(new WorkspaceAccess(owner.workspaceId(), owner.workspaceName(), "owner")));
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                session, null, List.of(new SimpleGrantedAuthority("ROLE_WORKSPACE"))));
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

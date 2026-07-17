package com.example.backend.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_INTEGRATION_TESTS", matches = "true")
class PostgresWorkspaceOwnershipSchemaIT {

  private static final String TEST_SCHEMA = "workspace_ownership_schema_test";

  private DriverManagerDataSource dataSource;
  private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    dataSource = new DriverManagerDataSource();
    dataSource.setUrl(withCurrentSchema(databaseUrl(), TEST_SCHEMA));
    dataSource.setUsername(requiredEnvironment("DATABASE_USERNAME"));
    dataSource.setPassword(requiredEnvironment("DATABASE_PASSWORD"));
    jdbcTemplate = new JdbcTemplate(dataSource);
  }

  @AfterEach
  void tearDown() {
    if (jdbcTemplate != null) {
      jdbcTemplate.execute("drop schema if exists " + TEST_SCHEMA + " cascade");
    }
  }

  @Test
  void requiresNewSnapshotsAndActiveConstraintsToBeWorkspaceScoped() {
    migrate(null);
    long firstWorkspaceId = createWorkspace("first@example.com", "First Workspace");
    long secondWorkspaceId = createWorkspace("second@example.com", "Second Workspace");

    insertSnapshot(firstWorkspaceId);
    insertSnapshot(secondWorkspaceId);

    Integer activeSnapshotCount =
        jdbcTemplate.queryForObject(
            "select count(*) from financial_record_snapshot where active", Integer.class);
    Integer workspaceIndexCount =
        jdbcTemplate.queryForObject(
            """
            select count(*)
            from pg_catalog.pg_indexes
            where schemaname = current_schema()
              and indexname in (
                  'uq_financial_record_snapshot_active_workspace',
                  'ix_financial_record_snapshot_workspace'
              )
            """,
            Integer.class);

    assertThat(activeSnapshotCount).isEqualTo(2);
    assertThat(workspaceIndexCount).isEqualTo(2);
    assertThatThrownBy(() -> insertSnapshot(firstWorkspaceId))
        .isInstanceOf(DataAccessException.class);
    assertThatThrownBy(() -> insertSnapshot(null)).isInstanceOf(DataAccessException.class);
  }

  @Test
  void retiresLegacyUnownedSnapshotsAndRequiresWorkspaceOwnership() {
    migrate(MigrationVersion.fromVersion("5"));
    insertLegacySnapshot();

    migrate(null);

    Integer unownedSnapshotCount =
        jdbcTemplate.queryForObject(
            "select count(*) from financial_record_snapshot where workspace_id is null",
            Integer.class);
    String workspaceNullable =
        jdbcTemplate.queryForObject(
            """
            select is_nullable
            from information_schema.columns
            where table_schema = current_schema()
              and table_name = 'financial_record_snapshot'
              and column_name = 'workspace_id'
            """,
            String.class);
    Integer retiredObjectCount =
        jdbcTemplate.queryForObject(
            """
            select count(*)
            from pg_catalog.pg_class object
            join pg_catalog.pg_namespace namespace on namespace.oid = object.relnamespace
            where namespace.nspname = current_schema()
              and object.relname in (
                  'financial_snapshot_document',
                  'financial_snapshot_workspace_migration',
                  'uq_financial_record_snapshot_active_unowned'
              )
            """,
            Integer.class);

    assertThat(unownedSnapshotCount).isZero();
    assertThat(workspaceNullable).isEqualTo("NO");
    assertThat(retiredObjectCount).isZero();
    assertThatThrownBy(() -> insertSnapshot(null)).isInstanceOf(DataAccessException.class);
  }

  @Test
  void retiresPopulatedV1TablesWithoutChangingWorkspaceRecords() {
    migrate(MigrationVersion.fromVersion("11"));
    long workspaceId = createWorkspace("v12@example.com", "V12 Workspace");
    insertSnapshot(workspaceId);
    insertLegacyV1Snapshot();

    migrate(null);

    Integer runtimeSnapshotCount =
        jdbcTemplate.queryForObject(
            "select count(*) from financial_record_snapshot where workspace_id = ?",
            Integer.class,
            workspaceId);
    Integer retiredObjectCount =
        jdbcTemplate.queryForObject(
            """
            select count(*)
            from pg_catalog.pg_class object
            join pg_catalog.pg_namespace namespace on namespace.oid = object.relnamespace
            where namespace.nspname = current_schema()
              and object.relname in (
                  'financial_snapshot',
                  'monthly_withdrawal',
                  'annual_withdrawal',
                  'asset_account',
                  'debt_account',
                  'income_summary_item',
                  'income_event',
                  'important_date'
              )
            """,
            Integer.class);

    assertThat(runtimeSnapshotCount).isOne();
    assertThat(retiredObjectCount).isZero();
  }

  private void migrate(MigrationVersion target) {
    FluentConfiguration configuration =
        Flyway.configure()
            .dataSource(dataSource)
            .defaultSchema(TEST_SCHEMA)
            .schemas(TEST_SCHEMA)
            .locations("classpath:db/migration")
            .validateMigrationNaming(true);
    if (target != null) {
      configuration.target(target);
    }
    configuration.load().migrate();
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
    return jdbcTemplate.queryForObject(
        """
        insert into workspace (name, created_by_user_id)
        values (?, ?)
        returning id
        """,
        Long.class,
        name,
        userId);
  }

  private void insertSnapshot(Long workspaceId) {
    jdbcTemplate.update(
        """
        insert into financial_record_snapshot
            (workspace_id, active, version, pay_period_start, pay_period_end)
        values (?, true, 1, date '2026-07-01', date '2026-07-14')
        """,
        workspaceId);
  }

  private void insertLegacySnapshot() {
    jdbcTemplate.update(
        """
        insert into financial_record_snapshot
            (active, version, pay_period_start, pay_period_end)
        values (true, 1, date '2026-07-01', date '2026-07-14')
        """);
  }

  private void insertLegacyV1Snapshot() {
    Long snapshotId =
        jdbcTemplate.queryForObject(
            """
            insert into financial_snapshot
                (active, version, pay_period_start, pay_period_end)
            values (true, 1, date '2026-07-01', date '2026-07-14')
            returning id
            """,
            Long.class);
    jdbcTemplate.update(
        """
        insert into monthly_withdrawal
            (snapshot_id, bill, due_day, amount, account, paid)
        values (?, 'Retired V1 Bill', 1, 10.00, 'Retired V1 Account', false)
        """,
        snapshotId);
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

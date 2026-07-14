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
                  'uq_financial_record_snapshot_active_unowned',
                  'ix_financial_record_snapshot_workspace'
              )
            """,
            Integer.class);

    assertThat(activeSnapshotCount).isEqualTo(2);
    assertThat(workspaceIndexCount).isEqualTo(3);
    assertThatThrownBy(() -> insertSnapshot(firstWorkspaceId))
        .isInstanceOf(DataAccessException.class);
    assertThatThrownBy(() -> insertSnapshot(null)).isInstanceOf(DataAccessException.class);
  }

  @Test
  void preservesLegacyUnownedSnapshotForExplicitMigration() {
    migrate(MigrationVersion.fromVersion("5"));
    insertLegacySnapshot();

    migrate(null);

    Integer unownedSnapshotCount =
        jdbcTemplate.queryForObject(
            "select count(*) from financial_record_snapshot where workspace_id is null",
            Integer.class);
    Boolean workspaceConstraintValidated =
        jdbcTemplate.queryForObject(
            """
            select convalidated
            from pg_catalog.pg_constraint
            where conrelid = 'financial_record_snapshot'::regclass
              and conname = 'ck_financial_record_snapshot_workspace_required'
            """,
            Boolean.class);

    assertThat(unownedSnapshotCount).isEqualTo(1);
    assertThat(workspaceConstraintValidated).isFalse();
    assertThatThrownBy(() -> insertSnapshot(null)).isInstanceOf(DataAccessException.class);
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

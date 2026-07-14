package com.example.backend.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_INTEGRATION_TESTS", matches = "true")
class PostgresIdentitySchemaIT {

  private static final String TEST_SCHEMA = "identity_schema_test";

  private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setUrl(withCurrentSchema(databaseUrl(), TEST_SCHEMA));
    dataSource.setUsername(requiredEnvironment("DATABASE_USERNAME"));
    dataSource.setPassword(requiredEnvironment("DATABASE_PASSWORD"));

    jdbcTemplate = new JdbcTemplate(dataSource);

    Flyway.configure()
        .dataSource(dataSource)
        .defaultSchema(TEST_SCHEMA)
        .schemas(TEST_SCHEMA)
        .locations("classpath:db/migration")
        .validateMigrationNaming(true)
        .load()
        .migrate();
  }

  @AfterEach
  void tearDown() {
    if (jdbcTemplate != null) {
      jdbcTemplate.execute("drop schema if exists " + TEST_SCHEMA + " cascade");
    }
  }

  @Test
  void createsIdentityTablesThroughFlyway() {
    List<String> identityTables =
        jdbcTemplate.queryForList(
            """
            select tablename
            from pg_catalog.pg_tables
            where schemaname = current_schema()
              and tablename in (
                  'application_user',
                  'workspace',
                  'workspace_membership',
                  'application_session'
              )
            order by tablename
            """,
            String.class);
    Integer appliedV5 =
        jdbcTemplate.queryForObject(
            "select count(*) from flyway_schema_history where version = '5' and success",
            Integer.class);

    assertThat(identityTables)
        .containsExactly(
            "application_session", "application_user", "workspace", "workspace_membership");
    assertThat(appliedV5).isEqualTo(1);
  }

  @Test
  void enforcesIdentityOwnershipAndSessionConstraints() {
    long ownerId = createUser("Owner@Example.com", "Owner");
    long memberId = createUser("member@example.com", "Member");
    long secondOwnerId = createUser("second-owner@example.com", "Second Owner");
    long invalidRoleUserId = createUser("viewer@example.com", "Viewer");
    long workspaceId = createWorkspace(ownerId, "Personal");

    jdbcTemplate.update(
        "insert into workspace_membership (workspace_id, user_id, role) values (?, ?, 'owner')",
        workspaceId,
        ownerId);
    jdbcTemplate.update(
        "insert into workspace_membership (workspace_id, user_id, role) values (?, ?, 'member')",
        workspaceId,
        memberId);
    jdbcTemplate.update(
        """
        insert into application_session (id, user_id, token_hash, expires_at)
        values (?, ?, ?, now() + interval '1 day')
        """,
        UUID.randomUUID(),
        ownerId,
        "synthetic-session-token-hash-00000000000000000000000000000001");

    String normalizedEmail =
        jdbcTemplate.queryForObject(
            "select normalized_email from application_user where id = ?", String.class, ownerId);
    Integer membershipCount =
        jdbcTemplate.queryForObject(
            "select count(*) from workspace_membership where workspace_id = ?",
            Integer.class,
            workspaceId);

    assertThat(normalizedEmail).isEqualTo("owner@example.com");
    assertThat(membershipCount).isEqualTo(2);

    assertThatThrownBy(() -> createUser("OWNER@example.com", "Duplicate"))
        .isInstanceOf(DataAccessException.class);
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "insert into workspace_membership (workspace_id, user_id, role) values (?, ?, 'owner')",
                    workspaceId,
                    secondOwnerId))
        .isInstanceOf(DataAccessException.class);
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "insert into workspace_membership (workspace_id, user_id, role) values (?, ?, 'viewer')",
                    workspaceId,
                    invalidRoleUserId))
        .isInstanceOf(DataAccessException.class);
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    insert into application_session (id, user_id, token_hash, expires_at)
                    values (?, ?, ?, now() - interval '1 minute')
                    """,
                    UUID.randomUUID(),
                    ownerId,
                    "synthetic-session-token-hash-00000000000000000000000000000002"))
        .isInstanceOf(DataAccessException.class);
  }

  private long createUser(String email, String displayName) {
    return jdbcTemplate.queryForObject(
        """
        insert into application_user (email, password_hash, display_name)
        values (?, ?, ?)
        returning id
        """,
        Long.class,
        email,
        "$synthetic$password$hash",
        displayName);
  }

  private long createWorkspace(long userId, String name) {
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

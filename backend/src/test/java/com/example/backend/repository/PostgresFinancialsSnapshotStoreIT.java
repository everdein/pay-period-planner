package com.example.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.backend.domain.financials.ExpenseBill;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.ObjectMapper;

@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_INTEGRATION_TESTS", matches = "true")
class PostgresFinancialsSnapshotStoreIT {

  private static final String TEST_SCHEMA = "financial_snapshot_store_test";

  private JdbcTemplate jdbcTemplate;
  private PostgresFinancialsSnapshotStore store;

  @BeforeEach
  void setUp() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setUrl(withCurrentSchema(databaseUrl(), TEST_SCHEMA));
    dataSource.setUsername(requiredEnvironment("DATABASE_USERNAME"));
    dataSource.setPassword(requiredEnvironment("DATABASE_PASSWORD"));

    jdbcTemplate = new JdbcTemplate(dataSource);
    jdbcTemplate.execute("create schema if not exists " + TEST_SCHEMA);
    jdbcTemplate.execute(
        """
        create table if not exists financial_snapshot_document (
            id bigint generated always as identity primary key,
            active boolean not null default true,
            version bigint not null default 1,
            snapshot_json jsonb not null,
            created_at timestamptz not null default now(),
            updated_at timestamptz not null default now()
        )
        """);
    jdbcTemplate.execute(
        """
        create unique index if not exists uq_financial_snapshot_document_active
            on financial_snapshot_document (active)
            where active
        """);
    jdbcTemplate.update("truncate table financial_snapshot_document restart identity");

    store =
        new PostgresFinancialsSnapshotStore(
            jdbcTemplate,
            new ObjectMapper(),
            Path.of("does-not-exist.local.json"),
            Path.of("does-not-exist.example.json"));
  }

  @AfterEach
  void tearDown() {
    if (jdbcTemplate != null) {
      jdbcTemplate.execute("drop schema if exists " + TEST_SCHEMA + " cascade");
    }
  }

  @Test
  void seedsEmptyDatabaseWithEmptySnapshot() {
    FinancialsData loaded = store.load();

    Integer rowCount =
        jdbcTemplate.queryForObject(
            "select count(*) from financial_snapshot_document where active = true", Integer.class);

    assertThat(loaded.bills()).isEmpty();
    assertThat(loaded.version()).isEqualTo(1L);
    assertThat(rowCount).isEqualTo(1);
  }

  @Test
  void savesLoadsAndIncrementsVersion() {
    FinancialsData seed = store.load();

    store.save(snapshotWithBill("Water", "31.25").withVersion(seed.version() + 1));
    store.save(snapshotWithBill("Electricity", "42.50").withVersion(seed.version() + 2));

    FinancialsData loaded = store.load();
    Long version =
        jdbcTemplate.queryForObject(
            "select version from financial_snapshot_document where active = true", Long.class);

    assertThat(loaded.bills()).hasSize(1);
    assertThat(loaded.version()).isEqualTo(3L);
    assertThat(loaded.bills().getFirst().bill()).isEqualTo("Electricity");
    assertThat(loaded.bills().getFirst().amount()).isEqualByComparingTo("42.50");
    assertThat(version).isEqualTo(3);
  }

  private FinancialsData snapshotWithBill(String billName, String amount) {
    return new FinancialsData(
        LocalDate.of(2026, 6, 12),
        LocalDate.of(2026, 6, 26),
        List.of(new ExpenseBill(1, billName, 10, new BigDecimal(amount), "Check", false)),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of());
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

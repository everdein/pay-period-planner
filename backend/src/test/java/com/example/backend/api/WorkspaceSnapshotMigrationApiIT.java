package com.example.backend.api;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.backend.config.WorkspaceSessionAuthenticationFilter;
import com.example.backend.domain.financials.AnnualWithdrawal;
import com.example.backend.domain.financials.AssetAccount;
import com.example.backend.domain.financials.DebtAccount;
import com.example.backend.domain.financials.ExpenseBill;
import com.example.backend.domain.financials.FinancialAuditEvent;
import com.example.backend.domain.financials.FinancialProjectionSummary;
import com.example.backend.domain.financials.ImportantDate;
import com.example.backend.domain.financials.IncomeEvent;
import com.example.backend.domain.financials.IncomeSummaryItem;
import com.example.backend.repository.FinancialsData;
import com.example.backend.service.AccountSessionService;
import com.example.backend.service.AccountSessionService.IssuedSession;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = {
      "financials.security.username=migration-test-user",
      "financials.security.password=migration-test-password",
      "financials.security.session-cookie-secure=false"
    })
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_INTEGRATION_TESTS", matches = "true")
class WorkspaceSnapshotMigrationApiIT {

  private static final String TEST_SCHEMA =
      "workspace_migration_api_" + UUID.randomUUID().toString().replace("-", "");
  private static final String MIGRATION_BASE = "/api/v1/admin/workspace-migrations";

  @Autowired private AccountSessionService accountSessionService;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @DynamicPropertySource
  static void postgresProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> withCurrentSchema(databaseUrl(), TEST_SCHEMA));
    registry.add("spring.datasource.username", () -> requiredEnvironment("DATABASE_USERNAME"));
    registry.add("spring.datasource.password", () -> requiredEnvironment("DATABASE_PASSWORD"));
    registry.add("spring.flyway.default-schema", () -> TEST_SCHEMA);
    registry.add("spring.flyway.schemas", () -> TEST_SCHEMA);
  }

  @AfterAll
  void tearDownSchema() {
    jdbcTemplate.execute("drop schema if exists " + TEST_SCHEMA + " cascade");
  }

  @Test
  void backsUpMigratesVerifiesAndRollsBackTheLegacyJsonbSnapshot() throws Exception {
    IssuedSession owner =
        accountSessionService.signUp(
            "jsonb-owner@example.com", "jsonb-owner-password-123", "JSONB Owner");
    long workspaceId = owner.session().workspaces().getFirst().workspaceId();
    FinancialsData source = syntheticData(7);
    writeLegacyJsonb(source);

    mockMvc
        .perform(get(MIGRATION_BASE + "/legacy-jsonb-backup"))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            get(MIGRATION_BASE + "/legacy-jsonb-backup")
                .cookie(
                    new Cookie(
                        WorkspaceSessionAuthenticationFilter.SESSION_COOKIE_NAME,
                        owner.rawToken())))
        .andExpect(status().isForbidden());

    MvcResult backup =
        mockMvc
            .perform(
                get(MIGRATION_BASE + "/legacy-jsonb-backup")
                    .with(httpBasic("migration-test-user", "migration-test-password")))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", containsString("no-store")))
            .andExpect(
                header()
                    .string(
                        "Content-Disposition", containsString("legacy-financial-snapshot.json")))
            .andExpect(header().string("X-Snapshot-Version", "7"))
            .andReturn();
    byte[] backupBytes = backup.getResponse().getContentAsByteArray();
    String fingerprint = sha256(backupBytes);

    MvcResult applied =
        mockMvc
            .perform(
                post(MIGRATION_BASE + "/apply/jsonb-document")
                    .with(httpBasic("migration-test-user", "migration-test-password"))
                    .header("X-Confirm-Financial-Migration", "APPLY")
                    .param("expectedFingerprint", fingerprint)
                    .param("destinationEmail", "jsonb-owner@example.com")
                    .param("workspaceId", Long.toString(workspaceId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("applied"))
            .andExpect(jsonPath("$.sourceKind").value("jsonb_document"))
            .andExpect(jsonPath("$.sourceVersion").value(7))
            .andExpect(jsonPath("$.metadataMatches").value(true))
            .andExpect(jsonPath("$.rollbackEligible").value(true))
            .andExpect(jsonPath("$.currentCounts.monthlyBills").value(2))
            .andExpect(jsonPath("$.currentCounts.assetAccounts").value(2))
            .andExpect(jsonPath("$.currentCounts.incomeSummaryItems").value(2))
            .andExpect(jsonPath("$.currentCounts.auditEvents").value(1))
            .andReturn();
    UUID migrationId = migrationId(applied);

    Long snapshotId =
        jdbcTemplate.queryForObject(
            "select migrated_snapshot_id from financial_snapshot_workspace_migration where id = ?",
            Long.class,
            migrationId);
    Long sourceDocumentId =
        jdbcTemplate.queryForObject(
            "select source_document_id from financial_record_snapshot where id = ?",
            Long.class,
            snapshotId);
    Integer auditCount =
        jdbcTemplate.queryForObject(
            "select count(*) from financial_record_audit_event where snapshot_id = ?",
            Integer.class,
            snapshotId);
    Integer projectionRoleCount =
        jdbcTemplate.queryForObject(
            "select count(*) from financial_record_projection_role where snapshot_id = ?",
            Integer.class,
            snapshotId);
    Long legacyVersion =
        jdbcTemplate.queryForObject(
            "select version from financial_snapshot_document where active", Long.class);

    org.assertj.core.api.Assertions.assertThat(sourceDocumentId).isNotNull();
    org.assertj.core.api.Assertions.assertThat(auditCount).isEqualTo(1);
    org.assertj.core.api.Assertions.assertThat(projectionRoleCount).isEqualTo(3);
    org.assertj.core.api.Assertions.assertThat(legacyVersion).isEqualTo(7);

    mockMvc
        .perform(
            get(MIGRATION_BASE + "/" + migrationId)
                .with(httpBasic("migration-test-user", "migration-test-password")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sourceFingerprint").value(fingerprint))
        .andExpect(jsonPath("$.snapshotActive").value(true))
        .andExpect(jsonPath("$.metadataMatches").value(true));

    mockMvc
        .perform(
            post(MIGRATION_BASE + "/" + migrationId + "/rollback")
                .with(httpBasic("migration-test-user", "migration-test-password"))
                .header("X-Confirm-Financial-Migration", "ROLLBACK"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("rolled_back"))
        .andExpect(jsonPath("$.snapshotActive").value(false))
        .andExpect(jsonPath("$.metadataMatches").value(true))
        .andExpect(jsonPath("$.rollbackEligible").value(false));

    Integer retainedRecordCount =
        jdbcTemplate.queryForObject(
            "select count(*) from financial_record_monthly_bill where snapshot_id = ?",
            Integer.class,
            snapshotId);
    Integer retainedProjectionRoleCount =
        jdbcTemplate.queryForObject(
            "select count(*) from financial_record_projection_role where snapshot_id = ?",
            Integer.class,
            snapshotId);
    org.assertj.core.api.Assertions.assertThat(retainedRecordCount).isEqualTo(2);
    org.assertj.core.api.Assertions.assertThat(retainedProjectionRoleCount).isEqualTo(3);
  }

  @Test
  void refusesFingerprintOwnerOverwriteAndChangedSnapshotRollback() throws Exception {
    IssuedSession owner =
        accountSessionService.signUp(
            "file-owner@example.com", "file-owner-password-123", "File Owner");
    IssuedSession other =
        accountSessionService.signUp(
            "other-owner@example.com", "other-owner-password-123", "Other Owner");
    long workspaceId = owner.session().workspaces().getFirst().workspaceId();
    byte[] source =
        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(syntheticData(9));
    String fingerprint = sha256(source);

    mockMvc
        .perform(
            post(MIGRATION_BASE + "/apply/json-file")
                .with(httpBasic("migration-test-user", "migration-test-password"))
                .header("X-Confirm-Financial-Migration", "APPLY")
                .param("expectedFingerprint", "0".repeat(64))
                .param("destinationEmail", "file-owner@example.com")
                .param("workspaceId", Long.toString(workspaceId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(source))
        .andExpect(status().isConflict());

    mockMvc
        .perform(
            post(MIGRATION_BASE + "/apply/json-file")
                .with(httpBasic("migration-test-user", "migration-test-password"))
                .header("X-Confirm-Financial-Migration", "APPLY")
                .param("expectedFingerprint", fingerprint)
                .param("destinationEmail", "other-owner@example.com")
                .param("workspaceId", Long.toString(workspaceId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(source))
        .andExpect(status().isNotFound());

    MvcResult applied =
        mockMvc
            .perform(
                post(MIGRATION_BASE + "/apply/json-file")
                    .with(httpBasic("migration-test-user", "migration-test-password"))
                    .header("X-Confirm-Financial-Migration", "APPLY")
                    .param("expectedFingerprint", fingerprint)
                    .param("destinationEmail", "file-owner@example.com")
                    .param("workspaceId", Long.toString(workspaceId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(source))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sourceKind").value("json_file"))
            .andReturn();
    UUID migrationId = migrationId(applied);

    mockMvc
        .perform(
            post(MIGRATION_BASE + "/apply/json-file")
                .with(httpBasic("migration-test-user", "migration-test-password"))
                .header("X-Confirm-Financial-Migration", "APPLY")
                .param("expectedFingerprint", fingerprint)
                .param("destinationEmail", "file-owner@example.com")
                .param("workspaceId", Long.toString(workspaceId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(source))
        .andExpect(status().isConflict());

    jdbcTemplate.update(
        """
        update financial_record_snapshot
        set version = version + 1
        where id = (
            select migrated_snapshot_id
            from financial_snapshot_workspace_migration
            where id = ?
        )
        """,
        migrationId);

    mockMvc
        .perform(
            post(MIGRATION_BASE + "/" + migrationId + "/rollback")
                .with(httpBasic("migration-test-user", "migration-test-password"))
                .header("X-Confirm-Financial-Migration", "ROLLBACK"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(containsString("changed")));

    org.assertj.core.api.Assertions.assertThat(
            other.session().workspaces().getFirst().workspaceId())
        .isNotEqualTo(workspaceId);
  }

  private void writeLegacyJsonb(FinancialsData data) throws Exception {
    String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);
    int updated =
        jdbcTemplate.update(
            """
            update financial_snapshot_document
            set version = ?, snapshot_json = ?::jsonb, updated_at = now()
            where active
            """,
            data.version(),
            json);
    if (updated == 0) {
      jdbcTemplate.update(
          """
          insert into financial_snapshot_document (active, version, snapshot_json)
          values (true, ?, ?::jsonb)
          """,
          data.version(),
          json);
    }
  }

  private UUID migrationId(MvcResult result) throws Exception {
    return UUID.fromString(
        objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("id").asText());
  }

  private FinancialsData syntheticData(long version) {
    LocalDate start = LocalDate.of(2026, 7, 1);
    LocalDate end = LocalDate.of(2026, 7, 14);
    FinancialProjectionSummary projection =
        new FinancialProjectionSummary(
            start,
            end,
            1,
            1,
            1,
            1,
            1,
            1,
            1,
            new BigDecimal("25.00"),
            new BigDecimal("50.00"),
            new BigDecimal("1000.00"),
            new BigDecimal("200.00"),
            new BigDecimal("800.00"));
    return new FinancialsData(
        version,
        start,
        end,
        List.of(new ExpenseBill(1, "Synthetic Bill", 5, new BigDecimal("25.00"), "Cash", false)),
        List.of(
            new AnnualWithdrawal(
                2, "Synthetic Annual", 6, 15, new BigDecimal("50.00"), "Cash", false)),
        List.of(
            new AssetAccount(
                3,
                "cash-savings",
                "Cash & Savings",
                "Synthetic Asset",
                "Synthetic Bank",
                new BigDecimal("1000.00"))),
        List.of(new DebtAccount(4, "Synthetic Debt", "Synthetic Lender", new BigDecimal("200.00"))),
        List.of(new IncomeSummaryItem(5, "Synthetic Income", "Monthly", new BigDecimal("500.00"))),
        List.of(new IncomeEvent(6, LocalDate.of(2026, 7, 3), "Synthetic Pay", "Paycheck", 10)),
        List.of(new ImportantDate(7, LocalDate.of(2026, 12, 1), "Synthetic Date", "Reminder")),
        List.of(
            new FinancialAuditEvent(
                8,
                Instant.parse("2026-07-10T12:00:00Z"),
                "UPDATE",
                "snapshot",
                null,
                version - 1,
                version,
                "Synthetic migration fixture",
                projection)));
  }

  private String sha256(byte[] bytes) throws Exception {
    return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  }

  private static String databaseUrl() {
    return environmentOrDefault("DATABASE_URL", "jdbc:postgresql://localhost:5432/financial_app");
  }

  private static String withCurrentSchema(String url, String schema) {
    String separator = url.contains("?") ? "&" : "?";
    return url + separator + "currentSchema=" + schema;
  }

  private static String environmentOrDefault(String name, String defaultValue) {
    String value = System.getenv(name);
    return value == null || value.isBlank() ? defaultValue : value;
  }

  private static String requiredEnvironment(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(name + " must be set for PostgreSQL integration tests");
    }
    return value;
  }
}

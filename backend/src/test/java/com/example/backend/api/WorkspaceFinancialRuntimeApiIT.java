package com.example.backend.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.backend.config.WorkspaceSessionAuthenticationFilter;
import com.example.backend.domain.financials.ExpenseBill;
import com.example.backend.domain.financials.FinancialSnapshot;
import com.example.backend.repository.PostgresFinancialRecordSnapshotAdapter;
import com.example.backend.service.AccountSessionService;
import com.example.backend.service.AccountSessionService.IssuedSession;
import com.example.backend.service.AuthenticatedWorkspaceResolver;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "financials.security.username=runtime-operator",
      "financials.security.password=runtime-operator-password",
      "financials.security.session-cookie-secure=false"
    })
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_INTEGRATION_TESTS", matches = "true")
class WorkspaceFinancialRuntimeApiIT {

  private static final String TEST_SCHEMA =
      "financial_runtime_api_" + UUID.randomUUID().toString().replace("-", "");

  @Autowired private AccountSessionService accountSessionService;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private MockMvc mockMvc;
  @Autowired private PostgresFinancialRecordSnapshotAdapter snapshotAdapter;

  private IssuedSession firstOwner;
  private IssuedSession secondOwner;
  private long firstWorkspaceId;
  private long secondWorkspaceId;

  @DynamicPropertySource
  static void postgresProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> withCurrentSchema(databaseUrl(), TEST_SCHEMA));
    registry.add("spring.datasource.username", () -> requiredEnvironment("DATABASE_USERNAME"));
    registry.add("spring.datasource.password", () -> requiredEnvironment("DATABASE_PASSWORD"));
    registry.add("spring.flyway.default-schema", () -> TEST_SCHEMA);
    registry.add("spring.flyway.schemas", () -> TEST_SCHEMA);
  }

  @BeforeAll
  void setUpWorkspaces() {
    firstOwner =
        accountSessionService.signUp(
            "first-runtime@example.com", "first-runtime-password-123", "First Runtime");
    secondOwner =
        accountSessionService.signUp(
            "second-runtime@example.com", "second-runtime-password-123", "Second Runtime");
    firstWorkspaceId = firstOwner.session().workspaces().getFirst().workspaceId();
    secondWorkspaceId = secondOwner.session().workspaces().getFirst().workspaceId();
  }

  @BeforeEach
  void resetFinancialSnapshots() {
    jdbcTemplate.update(
        "delete from financial_record_snapshot where workspace_id in (?, ?)",
        firstWorkspaceId,
        secondWorkspaceId);
    snapshotAdapter.createInitialSnapshot(
        firstWorkspaceId, snapshot(7, "First Workspace Bill", "31.25"));
    snapshotAdapter.createInitialSnapshot(
        secondWorkspaceId, snapshot(11, "Second Workspace Bill", "42.50"));
  }

  @AfterAll
  void tearDownSchema() {
    jdbcTemplate.execute("drop schema if exists " + TEST_SCHEMA + " cascade");
  }

  @Test
  void authorizesSessionsInsteadOfLegacyBasicCredentials() throws Exception {
    mockMvc.perform(get("/api/v1/financials")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            get("/api/v1/financials")
                .with(httpBasic("runtime-operator", "runtime-operator-password")))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(get("/api/v1/financials").cookie(sessionCookie(firstOwner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(7))
        .andExpect(jsonPath("$.bills[?(@.bill == 'First Workspace Bill')]").isNotEmpty())
        .andExpect(jsonPath("$.bills[?(@.bill == 'Second Workspace Bill')]").isEmpty());
  }

  @Test
  void isolatesWorkspaceReadsAndRejectsASelectedNonMembership() throws Exception {
    mockMvc
        .perform(get("/api/v1/financials").cookie(sessionCookie(secondOwner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(11))
        .andExpect(jsonPath("$.bills[?(@.bill == 'Second Workspace Bill')]").isNotEmpty())
        .andExpect(jsonPath("$.bills[?(@.bill == 'First Workspace Bill')]").isEmpty());

    mockMvc
        .perform(
            get("/api/v1/financials")
                .cookie(sessionCookie(firstOwner))
                .header(
                    AuthenticatedWorkspaceResolver.WORKSPACE_ID_HEADER,
                    Long.toString(secondWorkspaceId)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value("Workspace access denied"));
  }

  @Test
  void requiresCsrfAndPersistsMutationsOnlyInTheSelectedWorkspace() throws Exception {
    String requestBody =
        """
        {
          "bill": "First Workspace Added Bill",
          "dueDay": 15,
          "amount": 80.00,
          "account": "Check",
          "paid": false
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/financials/bills")
                .cookie(sessionCookie(firstOwner))
                .contentType("application/json")
                .content(requestBody))
        .andExpect(status().isForbidden());

    CsrfProof csrfProof = csrfProof();
    mockMvc
        .perform(
            post("/api/v1/financials/bills")
                .cookie(sessionCookie(firstOwner), csrfProof.cookie())
                .header(csrfProof.headerName(), csrfProof.token())
                .contentType("application/json")
                .content(requestBody))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.bill").value("First Workspace Added Bill"));

    mockMvc
        .perform(get("/api/v1/financials").cookie(sessionCookie(firstOwner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(8))
        .andExpect(jsonPath("$.bills[?(@.bill == 'First Workspace Added Bill')]").isNotEmpty())
        .andExpect(jsonPath("$.bills[?(@.bill == 'Second Workspace Bill')]").isEmpty());
    mockMvc
        .perform(get("/api/v1/financials/history").cookie(sessionCookie(firstOwner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.events.length()").value(1))
        .andExpect(jsonPath("$.events[0].versionBefore").value(7))
        .andExpect(jsonPath("$.events[0].versionAfter").value(8));
    mockMvc
        .perform(get("/api/v1/financials").cookie(sessionCookie(secondOwner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(11))
        .andExpect(jsonPath("$.bills[?(@.bill == 'Second Workspace Bill')]").isNotEmpty())
        .andExpect(jsonPath("$.bills[?(@.bill == 'First Workspace Added Bill')]").isEmpty());
  }

  private FinancialSnapshot snapshot(long version, String billName, String amount) {
    return new FinancialSnapshot(
        version,
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 14),
        List.of(new ExpenseBill(1, billName, 10, new BigDecimal(amount), "Check", false)),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }

  private Cookie sessionCookie(IssuedSession session) {
    return new Cookie(WorkspaceSessionAuthenticationFilter.SESSION_COOKIE_NAME, session.rawToken());
  }

  private CsrfProof csrfProof() throws Exception {
    org.springframework.test.web.servlet.MvcResult result =
        mockMvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
    Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
    if (cookie == null) {
      throw new IllegalStateException("CSRF response did not include the XSRF-TOKEN cookie");
    }
    String responseBody = result.getResponse().getContentAsString();
    return new CsrfProof(
        cookie,
        JsonPath.read(responseBody, "$.headerName"),
        JsonPath.read(responseBody, "$.token"));
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

  private record CsrfProof(Cookie cookie, String headerName, String token) {}
}

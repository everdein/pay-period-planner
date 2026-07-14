package com.example.backend.config;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.backend.domain.financials.FinancialSnapshot;
import com.example.backend.repository.PostgresFinancialRecordSnapshotAdapter;
import com.example.backend.service.AccountSessionService;
import com.example.backend.service.AccountSessionService.IssuedSession;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(
    properties = {
      "financials.security.username=test-user",
      "financials.security.password=test-password",
      "financials.security.allowed-origins=http://localhost:3000",
      "financials.security.max-request-bytes=256",
      "financials.security.session-cookie-secure=false"
    })
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_INTEGRATION_TESTS", matches = "true")
class ApiSecurityConfigIT {

  private static final String TEST_SCHEMA =
      "api_security_config_test_" + UUID.randomUUID().toString().replace("-", "");

  @Autowired private AccountSessionService accountSessionService;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private MockMvc mockMvc;
  @Autowired private PostgresFinancialRecordSnapshotAdapter snapshotAdapter;

  private IssuedSession owner;

  @DynamicPropertySource
  static void postgresProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> withCurrentSchema(databaseUrl(), TEST_SCHEMA));
    registry.add("spring.datasource.username", () -> requiredEnvironment("DATABASE_USERNAME"));
    registry.add("spring.datasource.password", () -> requiredEnvironment("DATABASE_PASSWORD"));
    registry.add("spring.flyway.default-schema", () -> TEST_SCHEMA);
    registry.add("spring.flyway.schemas", () -> TEST_SCHEMA);
  }

  @BeforeAll
  void setUpWorkspace() {
    owner =
        accountSessionService.signUp(
            "security-owner@example.com", "security-owner-password-123", "Security Owner");
    long workspaceId = owner.session().workspaces().getFirst().workspaceId();
    snapshotAdapter.createInitialSnapshot(
        workspaceId,
        new FinancialSnapshot(
            1,
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 14),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of()));
  }

  @AfterAll
  void tearDownSchema() {
    jdbcTemplate.execute("drop schema if exists " + TEST_SCHEMA + " cascade");
  }

  @Test
  void rejectsUnauthenticatedFinancialApiRequestsWithoutBrowserChallenge() throws Exception {
    mockMvc
        .perform(get("/api/v1/financials"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().doesNotExist("WWW-Authenticate"));
  }

  @Test
  void rejectsOperatorBasicCredentialsForFinancialApi() throws Exception {
    mockMvc
        .perform(get("/api/v1/financials").with(httpBasic("test-user", "test-password")))
        .andExpect(status().isForbidden())
        .andExpect(header().doesNotExist("WWW-Authenticate"));
  }

  @Test
  void allowsWorkspaceSessionFinancialApiRequests() throws Exception {
    mockMvc
        .perform(get("/api/v1/financials").cookie(sessionCookie()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1));
  }

  @Test
  void allowsHealthChecksWithoutAuthentication() throws Exception {
    mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
  }

  @Test
  void protectsMetricsWithOperatorCredentials() throws Exception {
    mockMvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());

    mockMvc
        .perform(get("/actuator/metrics").with(httpBasic("test-user", "test-password")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.names").isArray());
  }

  @Test
  void correlatesApiErrorsWithoutExposingAChallenge() throws Exception {
    CsrfProof csrfProof = csrfProof();
    mockMvc
        .perform(
            put("/api/v1/financials")
                .cookie(sessionCookie(), csrfProof.cookie())
                .header(csrfProof.headerName(), csrfProof.token())
                .header("X-Request-ID", "client-request-123")
                .contentType("application/json")
                .content("{"))
        .andExpect(status().isBadRequest())
        .andExpect(header().string("X-Request-ID", "client-request-123"))
        .andExpect(jsonPath("$.requestId").value("client-request-123"));
  }

  @Test
  void allowsConfiguredCorsPreflightRequests() throws Exception {
    mockMvc
        .perform(
            options("/api/v1/financials")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "PUT")
                .header(
                    "Access-Control-Request-Headers", "Content-Type, X-XSRF-TOKEN, X-Request-ID"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
        .andExpect(header().string("Access-Control-Allow-Headers", containsString("X-Request-ID")));
  }

  @Test
  void rejectsOversizedFinancialApiRequests() throws Exception {
    CsrfProof csrfProof = csrfProof();
    String oversizedPayload = "{\"padding\":\"" + "x".repeat(300) + "\"}";

    mockMvc
        .perform(
            put("/api/v1/financials")
                .cookie(sessionCookie(), csrfProof.cookie())
                .header(csrfProof.headerName(), csrfProof.token())
                .contentType("application/json")
                .content(oversizedPayload))
        .andExpect(status().isPayloadTooLarge());
  }

  private CsrfProof csrfProof() throws Exception {
    MvcResult result =
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

  private Cookie sessionCookie() {
    return new Cookie(WorkspaceSessionAuthenticationFilter.SESSION_COOKIE_NAME, owner.rawToken());
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

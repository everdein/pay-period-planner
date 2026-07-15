package com.example.backend.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.backend.config.WorkspaceSessionAuthenticationFilter;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
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
      "financials.security.username=legacy-test-user",
      "financials.security.password=legacy-test-password",
      "financials.security.session-cookie-secure=false"
    })
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_INTEGRATION_TESTS", matches = "true")
class AccountSessionApiIT {

  private static final String TEST_SCHEMA =
      "account_session_api_test_" + UUID.randomUUID().toString().replace("-", "");

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private MockMvc mockMvc;

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
  void signsUpRecoversAndRevokesAnHttpOnlyWorkspaceSession() throws Exception {
    CsrfProof csrfProof = csrfProof();

    mockMvc
        .perform(
            post("/api/v1/auth/signup")
                .contentType("application/json")
                .content(
                    """
                    {
                      "email": "missing-csrf@example.com",
                      "password": "missing-csrf-password-123",
                      "displayName": "Missing CSRF"
                    }
                    """))
        .andExpect(status().isForbidden());

    MvcResult signUp =
        mockMvc
            .perform(
                post("/api/v1/auth/signup")
                    .cookie(csrfProof.cookie())
                    .header(csrfProof.headerName(), csrfProof.token())
                    .contentType("application/json")
                    .content(
                        """
                        {
                          "email": "api-owner@example.com",
                          "password": "api-owner-password-123",
                          "displayName": "API Owner"
                        }
                        """))
            .andExpect(status().isCreated())
            .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
            .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")))
            .andExpect(header().string("Set-Cookie", not(containsString("Secure"))))
            .andExpect(jsonPath("$.email").value("api-owner@example.com"))
            .andExpect(jsonPath("$.workspaces[0].name").value("Personal"))
            .andExpect(jsonPath("$.workspaces[0].role").value("owner"))
            .andReturn();
    Cookie sessionCookie = sessionCookie(signUp);

    mockMvc
        .perform(get("/api/v1/auth/session").cookie(sessionCookie))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName").value("API Owner"));

    mockMvc
        .perform(get("/api/v1/financials").cookie(sessionCookie))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("Financial snapshot not found"));

    mockMvc
        .perform(
            post("/api/v1/financials")
                .cookie(sessionCookie, csrfProof.cookie())
                .header(csrfProof.headerName(), csrfProof.token())
                .contentType("application/json")
                .content(
                    """
                    {
                      "startDate": "2026-07-10",
                      "endDate": "2026-07-23"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.version").value(1))
        .andExpect(jsonPath("$.payPeriodStart").value("2026-07-10"))
        .andExpect(jsonPath("$.payPeriodEnd").value("2026-07-23"))
        .andExpect(jsonPath("$.bills[0].bill").value("Rent"))
        .andExpect(jsonPath("$.bills[0].amount").value(0))
        .andExpect(jsonPath("$.assetCategories.length()").value(4));

    mockMvc
        .perform(
            post("/api/v1/financials")
                .cookie(sessionCookie, csrfProof.cookie())
                .header(csrfProof.headerName(), csrfProof.token())
                .contentType("application/json")
                .content(
                    """
                    {
                      "startDate": "2026-07-24",
                      "endDate": "2026-08-06"
                    }
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("Financial snapshot already exists"));

    mockMvc
        .perform(get("/api/v1/financials").cookie(sessionCookie))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1));

    mockMvc
        .perform(
            get("/api/v1/financials").with(httpBasic("legacy-test-user", "legacy-test-password")))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            post("/api/v1/auth/signout")
                .cookie(sessionCookie, csrfProof.cookie())
                .header(csrfProof.headerName(), csrfProof.token()))
        .andExpect(status().isNoContent())
        .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));

    mockMvc
        .perform(get("/api/v1/auth/session").cookie(sessionCookie))
        .andExpect(status().isUnauthorized())
        .andExpect(header().doesNotExist("WWW-Authenticate"));
  }

  @Test
  void isolatesTwoBrowserSessionsAndRejectsInvalidOrDuplicateCredentials() throws Exception {
    Cookie first = signUp("first-api@example.com", "first-api-password-123", "First API");
    Cookie second = signUp("second-api@example.com", "second-api-password-123", "Second API");
    CsrfProof csrfProof = csrfProof();

    mockMvc
        .perform(get("/api/v1/auth/session").cookie(first))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("first-api@example.com"))
        .andExpect(jsonPath("$.workspaces[0].name").value("Personal"));
    mockMvc
        .perform(get("/api/v1/auth/session").cookie(second))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("second-api@example.com"))
        .andExpect(jsonPath("$.workspaces[0].name").value("Personal"));

    mockMvc
        .perform(
            post("/api/v1/auth/signin")
                .cookie(csrfProof.cookie())
                .header(csrfProof.headerName(), csrfProof.token())
                .contentType("application/json")
                .content(
                    """
                    {"email":"first-api@example.com","password":"wrong-password-123"}
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.detail").value("Email or password was not accepted"));

    mockMvc
        .perform(
            post("/api/v1/auth/signup")
                .cookie(csrfProof.cookie())
                .header(csrfProof.headerName(), csrfProof.token())
                .contentType("application/json")
                .content(
                    """
                    {
                      "email":"FIRST-API@example.com",
                      "password":"duplicate-password-123",
                      "displayName":"Duplicate"
                    }
                    """))
        .andExpect(status().isConflict());
  }

  private Cookie signUp(String email, String password, String displayName) throws Exception {
    CsrfProof csrfProof = csrfProof();
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/signup")
                    .cookie(csrfProof.cookie())
                    .header(csrfProof.headerName(), csrfProof.token())
                    .contentType("application/json")
                    .content(
                        """
                        {"email":"%s","password":"%s","displayName":"%s"}
                        """
                            .formatted(email, password, displayName)))
            .andExpect(status().isCreated())
            .andReturn();
    return sessionCookie(result);
  }

  private CsrfProof csrfProof() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/api/v1/auth/csrf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
            .andReturn();
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

  private Cookie sessionCookie(MvcResult result) {
    String setCookie = result.getResponse().getHeader("Set-Cookie");
    if (setCookie == null) {
      throw new IllegalStateException("Signup response did not include a session cookie");
    }
    String rawValue = setCookie.substring(0, setCookie.indexOf(';'));
    String prefix = WorkspaceSessionAuthenticationFilter.SESSION_COOKIE_NAME + "=";
    return new Cookie(
        WorkspaceSessionAuthenticationFilter.SESSION_COOKIE_NAME,
        rawValue.substring(prefix.length()));
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

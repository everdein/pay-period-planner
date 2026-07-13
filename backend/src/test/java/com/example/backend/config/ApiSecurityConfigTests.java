package com.example.backend.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "financials.data.path=target/test-data/security-financials.local.json",
      "financials.security.username=test-user",
      "financials.security.password=test-password",
      "financials.security.allowed-origins=http://localhost:3000",
      "financials.security.max-request-bytes=64"
    })
@AutoConfigureMockMvc
class ApiSecurityConfigTests {

  @Autowired private MockMvc mockMvc;

  @Test
  void rejectsUnauthenticatedFinancialApiRequests() throws Exception {
    mockMvc
        .perform(get("/api/v1/financials"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().doesNotExist("WWW-Authenticate"));
  }

  @Test
  void rejectsInvalidFinancialApiCredentialsWithoutBrowserChallenge() throws Exception {
    mockMvc
        .perform(get("/api/v1/financials").with(httpBasic("test-user", "wrong-password")))
        .andExpect(status().isUnauthorized())
        .andExpect(header().doesNotExist("WWW-Authenticate"));
  }

  @Test
  void allowsAuthenticatedFinancialApiRequests() throws Exception {
    mockMvc
        .perform(get("/api/v1/financials").with(httpBasic("test-user", "test-password")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").exists());
  }

  @Test
  void allowsHealthChecksWithoutAuthentication() throws Exception {
    mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
  }

  @Test
  void allowsConfiguredCorsPreflightRequests() throws Exception {
    mockMvc
        .perform(
            options("/api/v1/financials")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "PUT")
                .header("Access-Control-Request-Headers", "Authorization, Content-Type"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
  }

  @Test
  void rejectsOversizedFinancialApiRequests() throws Exception {
    String oversizedPayload =
        """
        {"version":1,"payPeriodStart":"2026-06-12","payPeriodEnd":"2026-06-26","bills":[]}
        """;

    mockMvc
        .perform(
            put("/api/v1/financials")
                .with(httpBasic("test-user", "test-password"))
                .contentType("application/json")
                .content(oversizedPayload))
        .andExpect(status().isPayloadTooLarge());
  }
}

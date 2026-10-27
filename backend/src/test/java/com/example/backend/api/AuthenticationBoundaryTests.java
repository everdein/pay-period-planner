package com.example.backend.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.backend.config.FinancialsSecurityProperties;
import com.example.backend.config.RegistrationMode;
import com.example.backend.config.RequestSizeLimitFilter;
import com.example.backend.service.AccountSessionService;
import com.example.backend.service.AuthenticationAttemptLimiter;
import com.example.backend.support.InMemoryAccountSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthenticationBoundaryTests {
  private final CountingEncoder encoder = new CountingEncoder();

  private MockMvc mvc() {
    FinancialsSecurityProperties properties = new FinancialsSecurityProperties();
    return mvc(properties);
  }

  private MockMvc mvc(FinancialsSecurityProperties properties) {
    AccountSessionService service =
        new AccountSessionService(new InMemoryAccountSessionRepository(), encoder, properties);
    encoder.calls = 0;
    return MockMvcBuilders.standaloneSetup(
            new AccountSessionController(service, properties, new AuthenticationAttemptLimiter()))
        .setControllerAdvice(new ApiExceptionHandler())
        .addFilters(new RequestSizeLimitFilter(properties))
        .build();
  }

  @Test
  void closedRegistrationReturnsGeneric403WithoutPasswordWork() throws Exception {
    FinancialsSecurityProperties properties = new FinancialsSecurityProperties();
    properties.setRegistrationMode(RegistrationMode.DISABLED);
    MockMvc mvc = mvc(properties);
    mvc.perform(get("/api/v1/auth/registration"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.registrationEnabled").value(false));
    mvc.perform(
            post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\"owner@example.test\",\"password\":\"owner-password-123\",\"displayName\":\"Owner\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.detail").value("Account registration is unavailable"));
    assertThat(encoder.calls).isZero();
  }

  @Test
  void bootstrapHeaderPermitsOneAccountAndIsNeverReturned() throws Exception {
    String token = "synthetic-bootstrap-token-at-least-32-characters";
    FinancialsSecurityProperties properties = new FinancialsSecurityProperties();
    properties.setRegistrationMode(RegistrationMode.OWNER_BOOTSTRAP);
    properties.setOwnerBootstrapTokenHash(
        java.util.HexFormat.of()
            .formatHex(
                java.security.MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
    MockMvc mvc = mvc(properties);
    String body =
        "{\"email\":\"owner@example.test\",\"password\":\"owner-password-123\",\"displayName\":\"Owner\"}";
    mvc.perform(get("/api/v1/auth/registration"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.registrationEnabled").value(false));
    mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isForbidden());
    var response =
        mvc.perform(
                post("/api/v1/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Owner-Bootstrap-Token", token)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse();
    assertThat(response.getContentAsString()).doesNotContain(token);
    assertThat(response.getHeader("X-Owner-Bootstrap-Token")).isNull();
    mvc.perform(
            post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Owner-Bootstrap-Token", token)
                .content(body))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.detail").value("Account registration is unavailable"));
    assertThat(encoder.calls).isEqualTo(1);
  }

  @Test
  void sharedSignupSigninAccountBudgetReturns429BeforeHashing() throws Exception {
    MockMvc mvc = mvc();
    for (int index = 0; index < 9; index++) {
      mvc.perform(
              post("/api/v1/auth/signin")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"email\":\"owner@example.com\",\"password\":\"wrong-password-123\"}"))
          .andExpect(status().isUnauthorized());
    }
    mvc.perform(
            post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\"OWNER@example.com\",\"password\":\"owner-password-123\",\"displayName\":\"Owner\"}"))
        .andExpect(status().isCreated());
    mvc.perform(
            post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\"owner@example.com\",\"password\":\"owner-password-123\",\"displayName\":\"Owner\"}"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("Retry-After", "60"));
    assertThat(encoder.calls).isEqualTo(10);
  }

  @Test
  void spoofedForwardingHeadersCannotResetSourceBudget() throws Exception {
    MockMvc mvc = mvc();
    for (int index = 0; index < 31; index++) {
      mvc.perform(
              post("/api/v1/auth/signin")
                  .contentType(MediaType.APPLICATION_JSON)
                  .header("X-Forwarded-For", "192.0.2." + index)
                  .header("Forwarded", "for=192.0.2." + index)
                  .content(
                      "{\"email\":\"account"
                          + index
                          + "@example.com\",\"password\":\"wrong-password-123\"}"))
          .andExpect(index < 30 ? status().isUnauthorized() : status().isTooManyRequests());
    }
    assertThat(encoder.calls).isEqualTo(30);
    mvc.perform(
            post("/api/v1/auth/signin")
                .with(
                    request -> {
                      request.setRemoteAddr("192.0.2.100");
                      return request;
                    })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"other@example.com\",\"password\":\"wrong-password-123\"}"))
        .andExpect(status().isUnauthorized());
    assertThat(encoder.calls).isEqualTo(31);
  }

  @Test
  void malformedAndInvalidJsonStillReturn400WithoutHashing() throws Exception {
    MockMvc mvc = mvc();
    for (String body : new String[] {"{", "{\"email\":\"invalid\",\"password\":\"\"}"}) {
      mvc.perform(post("/api/v1/auth/signin").contentType(MediaType.APPLICATION_JSON).content(body))
          .andExpect(status().isBadRequest());
    }
    assertThat(encoder.calls).isZero();
  }

  private static class CountingEncoder implements PasswordEncoder {
    private int calls;

    @Override
    public String encode(CharSequence raw) {
      calls++;
      return "synthetic-hash";
    }

    @Override
    public boolean matches(CharSequence raw, String encoded) {
      calls++;
      return false;
    }
  }
}

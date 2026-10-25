package com.example.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.backend.service.AuthenticationAttemptLimiter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class BasicAuthenticationBudgetFilterTests {
  @Test
  void limitsBasicCredentialsBeforeDownstreamPasswordWork() throws Exception {
    BasicAuthenticationBudgetFilter filter =
        new BasicAuthenticationBudgetFilter(new AuthenticationAttemptLimiter());
    AtomicInteger work = new AtomicInteger();
    for (int index = 0; index < 31; index++) {
      MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/signin");
      request.setRemoteAddr("192.0.2.10");
      request.addHeader(
          "Authorization",
          "Basic "
              + Base64.getEncoder()
                  .encodeToString(
                      ("unknown-" + index + ":wrong-password").getBytes(StandardCharsets.UTF_8)));
      MockHttpServletResponse response = new MockHttpServletResponse();
      filter.doFilter(
          request,
          response,
          (req, res) -> {
            work.incrementAndGet();
            ((jakarta.servlet.http.HttpServletResponse) res).setStatus(401);
          });
      assertThat(response.getStatus()).isEqualTo(index < 30 ? 401 : 429);
      if (index == 30) assertThat(response.getHeader("Retry-After")).isEqualTo("60");
    }
    assertThat(work.get()).isEqualTo(30);
  }

  @Test
  void preservesRequestsWithoutBasicAndRejectsMalformedBasic() throws Exception {
    BasicAuthenticationBudgetFilter filter =
        new BasicAuthenticationBudgetFilter(new AuthenticationAttemptLimiter());
    AtomicInteger calls = new AtomicInteger();
    filter.doFilter(
        new MockHttpServletRequest(),
        new MockHttpServletResponse(),
        (req, res) -> calls.incrementAndGet());
    MockHttpServletRequest malformed = new MockHttpServletRequest();
    malformed.addHeader("Authorization", "Basic !invalid!");
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(malformed, response, (req, res) -> calls.incrementAndGet());
    assertThat(calls.get()).isEqualTo(1);
    assertThat(response.getStatus()).isEqualTo(401);
  }
}

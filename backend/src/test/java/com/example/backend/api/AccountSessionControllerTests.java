package com.example.backend.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.backend.config.FinancialsSecurityProperties;
import com.example.backend.config.WorkspaceSessionAuthenticationFilter;
import com.example.backend.domain.identity.AuthenticatedSession;
import com.example.backend.dto.identity.AccountSignInRequest;
import com.example.backend.dto.identity.AccountSignUpRequest;
import com.example.backend.service.AccountSessionService;
import com.example.backend.support.InMemoryAccountSessionRepository;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.web.csrf.CsrfToken;

class AccountSessionControllerTests {

  @Test
  void createsRecoversSignsInAndRevokesBrowserSessions() {
    InMemoryAccountSessionRepository repository = new InMemoryAccountSessionRepository();
    FinancialsSecurityProperties properties = properties(false);
    AccountSessionService service = service(repository, properties);
    AccountSessionController controller = new AccountSessionController(service, properties);

    var csrfResponse = controller.csrfResponse(csrfToken());
    var signupResponse =
        controller.signUp(
            new AccountSignUpRequest("owner@example.com", "owner-password-123", "Owner"));
    String sessionCookie = signupResponse.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
    String rawToken = cookieValue(sessionCookie);
    AuthenticatedSession session = service.recover(rawToken).orElseThrow();

    assertThat(csrfResponse.headerName()).isEqualTo("X-XSRF-TOKEN");
    assertThat(csrfResponse.token()).isEqualTo("csrf-token");
    assertThat(signupResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(signupResponse.getBody().workspaces()).singleElement();
    assertThat(sessionCookie)
        .contains(WorkspaceSessionAuthenticationFilter.SESSION_COOKIE_NAME + "=")
        .contains("HttpOnly")
        .contains("SameSite=Strict")
        .doesNotContain("Secure");
    assertThat(controller.recover(session).email()).isEqualTo("owner@example.com");

    var signinResponse =
        controller.signIn(new AccountSignInRequest("OWNER@example.com", "owner-password-123"));
    assertThat(signinResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

    var signoutResponse = controller.signOut(session);
    assertThat(signoutResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(signoutResponse.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
    assertThat(service.recover(rawToken)).isEmpty();
  }

  @Test
  void marksSessionCookiesSecureWhenConfigured() {
    InMemoryAccountSessionRepository repository = new InMemoryAccountSessionRepository();
    FinancialsSecurityProperties properties = properties(true);
    AccountSessionController controller =
        new AccountSessionController(service(repository, properties), properties);

    String sessionCookie =
        controller
            .signUp(new AccountSignUpRequest("secure@example.com", "secure-password-123", "Secure"))
            .getHeaders()
            .getFirst(HttpHeaders.SET_COOKIE);

    assertThat(sessionCookie).contains("Secure");
  }

  private AccountSessionService service(
      InMemoryAccountSessionRepository repository, FinancialsSecurityProperties properties) {
    return new AccountSessionService(
        repository, PasswordEncoderFactories.createDelegatingPasswordEncoder(), properties);
  }

  private FinancialsSecurityProperties properties(boolean secureCookie) {
    FinancialsSecurityProperties properties = new FinancialsSecurityProperties();
    properties.setSessionDuration(Duration.ofHours(1));
    properties.setSessionCookieSecure(secureCookie);
    return properties;
  }

  private CsrfToken csrfToken() {
    return new CsrfToken() {
      @Override
      public String getHeaderName() {
        return "X-XSRF-TOKEN";
      }

      @Override
      public String getParameterName() {
        return "_csrf";
      }

      @Override
      public String getToken() {
        return "csrf-token";
      }
    };
  }

  private String cookieValue(String setCookie) {
    if (setCookie == null) {
      throw new IllegalArgumentException("Set-Cookie is required");
    }
    String pair = setCookie.substring(0, setCookie.indexOf(';'));
    return pair.substring(pair.indexOf('=') + 1);
  }
}

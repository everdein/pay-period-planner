package com.example.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.backend.service.AccountSessionService;
import com.example.backend.service.AccountSessionService.IssuedSession;
import com.example.backend.support.InMemoryAccountSessionRepository;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

class WorkspaceSessionAuthenticationFilterTests {

  private AccountSessionService service;
  private WorkspaceSessionAuthenticationFilter filter;

  @BeforeEach
  void setUp() {
    FinancialsSecurityProperties properties = new FinancialsSecurityProperties();
    properties.setSessionDuration(Duration.ofHours(1));
    service =
        new AccountSessionService(
            new InMemoryAccountSessionRepository(),
            PasswordEncoderFactories.createDelegatingPasswordEncoder(),
            properties);
    filter = new WorkspaceSessionAuthenticationFilter(service);
    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void authenticatesAValidWorkspaceSessionCookie() throws Exception {
    IssuedSession issued = service.signUp("owner@example.com", "owner-password-123", "Owner");
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(
        new Cookie(WorkspaceSessionAuthenticationFilter.SESSION_COOKIE_NAME, issued.rawToken()));

    filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

    var authentication = SecurityContextHolder.getContext().getAuthentication();
    assertThat(authentication).isNotNull();
    assertThat(authentication.getName()).isEqualTo("owner@example.com");
    assertThat(authentication.getAuthorities())
        .extracting(Object::toString)
        .containsExactly("ROLE_WORKSPACE");
  }

  @Test
  void ignoresMissingInvalidBlankAndPreAuthenticatedRequests() throws Exception {
    filter.doFilter(
        new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

    MockHttpServletRequest invalid = new MockHttpServletRequest();
    invalid.setCookies(
        new Cookie(WorkspaceSessionAuthenticationFilter.SESSION_COOKIE_NAME, "invalid"),
        new Cookie("unrelated", "value"));
    filter.doFilter(invalid, new MockHttpServletResponse(), new MockFilterChain());
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

    MockHttpServletRequest blank = new MockHttpServletRequest();
    blank.setCookies(new Cookie(WorkspaceSessionAuthenticationFilter.SESSION_COOKIE_NAME, ""));
    filter.doFilter(blank, new MockHttpServletResponse(), new MockFilterChain());
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

    TestingAuthenticationToken existing =
        new TestingAuthenticationToken("legacy", "", "ROLE_FINANCIALS");
    SecurityContextHolder.getContext().setAuthentication(existing);
    filter.doFilter(invalid, new MockHttpServletResponse(), new MockFilterChain());
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(existing);
  }
}

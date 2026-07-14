package com.example.backend.api;

import com.example.backend.config.FinancialsSecurityProperties;
import com.example.backend.config.WorkspaceSessionAuthenticationFilter;
import com.example.backend.domain.identity.AuthenticatedSession;
import com.example.backend.dto.identity.AccountSessionResponse;
import com.example.backend.dto.identity.AccountSignInRequest;
import com.example.backend.dto.identity.AccountSignUpRequest;
import com.example.backend.dto.identity.CsrfTokenResponse;
import com.example.backend.service.AccountSessionService;
import com.example.backend.service.AccountSessionService.IssuedSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AccountSessionController {

  private final AccountSessionService accountSessionService;
  private final FinancialsSecurityProperties securityProperties;

  public AccountSessionController(
      AccountSessionService accountSessionService,
      FinancialsSecurityProperties securityProperties) {
    this.accountSessionService = accountSessionService;
    this.securityProperties = securityProperties;
  }

  @GetMapping("/csrf")
  public CsrfTokenResponse csrf(HttpServletRequest request) {
    Object csrfToken = request.getAttribute(CsrfToken.class.getName());
    if (!(csrfToken instanceof CsrfToken token)) {
      throw new IllegalStateException("CSRF protection is not available for this request");
    }
    return csrfResponse(token);
  }

  CsrfTokenResponse csrfResponse(CsrfToken csrfToken) {
    return CsrfTokenResponse.from(csrfToken);
  }

  @PostMapping("/signup")
  public ResponseEntity<AccountSessionResponse> signUp(
      @Valid @RequestBody AccountSignUpRequest request) {
    IssuedSession issued =
        accountSessionService.signUp(request.email(), request.password(), request.displayName());
    return sessionResponse(issued, HttpStatus.CREATED);
  }

  @PostMapping("/signin")
  public ResponseEntity<AccountSessionResponse> signIn(
      @Valid @RequestBody AccountSignInRequest request) {
    IssuedSession issued = accountSessionService.signIn(request.email(), request.password());
    return sessionResponse(issued, HttpStatus.OK);
  }

  @GetMapping("/session")
  public AccountSessionResponse recover(
      @AuthenticationPrincipal AuthenticatedSession authenticatedSession) {
    return AccountSessionResponse.from(authenticatedSession);
  }

  @PostMapping("/signout")
  public ResponseEntity<Void> signOut(
      @AuthenticationPrincipal AuthenticatedSession authenticatedSession) {
    accountSessionService.signOut(authenticatedSession.sessionId());
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, expiredSessionCookie().toString())
        .build();
  }

  private ResponseEntity<AccountSessionResponse> sessionResponse(
      IssuedSession issuedSession, HttpStatus status) {
    return ResponseEntity.status(status)
        .header(HttpHeaders.SET_COOKIE, sessionCookie(issuedSession).toString())
        .body(AccountSessionResponse.from(issuedSession.session()));
  }

  private ResponseCookie sessionCookie(IssuedSession issuedSession) {
    return ResponseCookie.from(
            WorkspaceSessionAuthenticationFilter.SESSION_COOKIE_NAME, issuedSession.rawToken())
        .httpOnly(true)
        .secure(securityProperties.sessionCookieSecure())
        .sameSite("Strict")
        .path("/")
        .maxAge(securityProperties.sessionDuration())
        .build();
  }

  private ResponseCookie expiredSessionCookie() {
    return ResponseCookie.from(WorkspaceSessionAuthenticationFilter.SESSION_COOKIE_NAME, "")
        .httpOnly(true)
        .secure(securityProperties.sessionCookieSecure())
        .sameSite("Strict")
        .path("/")
        .maxAge(0)
        .build();
  }
}

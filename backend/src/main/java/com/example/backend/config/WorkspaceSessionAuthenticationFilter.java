package com.example.backend.config;

import com.example.backend.service.AccountSessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class WorkspaceSessionAuthenticationFilter extends OncePerRequestFilter {

  public static final String SESSION_COOKIE_NAME = "financials_session";
  public static final String WORKSPACE_ROLE = "WORKSPACE";

  private final AccountSessionService accountSessionService;

  public WorkspaceSessionAuthenticationFilter(AccountSessionService accountSessionService) {
    this.accountSessionService = accountSessionService;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (SecurityContextHolder.getContext().getAuthentication() == null) {
      sessionToken(request)
          .flatMap(accountSessionService::recover)
          .ifPresent(
              (session) ->
                  SecurityContextHolder.getContext()
                      .setAuthentication(
                          UsernamePasswordAuthenticationToken.authenticated(
                              session,
                              null,
                              java.util.List.of(
                                  new SimpleGrantedAuthority("ROLE_" + WORKSPACE_ROLE)))));
    }

    filterChain.doFilter(request, response);
  }

  private java.util.Optional<String> sessionToken(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return java.util.Optional.empty();
    }

    return Arrays.stream(cookies)
        .filter((cookie) -> SESSION_COOKIE_NAME.equals(cookie.getName()))
        .map(Cookie::getValue)
        .filter((value) -> !value.isBlank())
        .findFirst();
  }
}

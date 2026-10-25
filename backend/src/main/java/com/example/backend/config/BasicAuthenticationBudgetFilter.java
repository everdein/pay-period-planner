package com.example.backend.config;

import com.example.backend.service.AuthenticationAttemptLimiter;
import com.example.backend.service.AuthenticationThrottledException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.www.BasicAuthenticationConverter;
import org.springframework.web.filter.OncePerRequestFilter;

/** Admit Basic authentication before the security chain performs password verification. */
public class BasicAuthenticationBudgetFilter extends OncePerRequestFilter {
  private final AuthenticationAttemptLimiter limiter;
  private final BasicAuthenticationConverter converter = new BasicAuthenticationConverter();

  public BasicAuthenticationBudgetFilter(AuthenticationAttemptLimiter limiter) {
    this.limiter = limiter;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Authentication basic;
    try {
      basic = converter.convert(request);
    } catch (AuthenticationException exception) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }
    if (basic == null) {
      chain.doFilter(request, response);
      return;
    }
    try (var permit = limiter.acquire(basic.getName(), request.getRemoteAddr())) {
      chain.doFilter(request, response);
    } catch (AuthenticationThrottledException exception) {
      response.setStatus(429);
      response.setHeader("Retry-After", "60");
      response.setContentType("application/problem+json");
      response
          .getWriter()
          .write(
              "{\"type\":\"about:blank\",\"title\":\"Authentication temporarily limited\","
                  + "\"status\":429,\"detail\":\"Too many authentication attempts; try again later\"}");
    }
  }
}

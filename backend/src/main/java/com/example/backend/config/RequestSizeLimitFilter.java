package com.example.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestSizeLimitFilter extends OncePerRequestFilter {

  private final FinancialsSecurityProperties securityProperties;

  public RequestSizeLimitFilter(FinancialsSecurityProperties securityProperties) {
    this.securityProperties = securityProperties;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    long contentLength = request.getContentLengthLong();
    long maxRequestBytes = securityProperties.maxRequestBytes();

    if (contentLength > maxRequestBytes) {
      response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
      response.setContentType(MediaType.TEXT_PLAIN_VALUE);
      response.getWriter().write("Request body exceeds the configured maximum size");
      return;
    }

    filterChain.doFilter(request, response);
  }
}

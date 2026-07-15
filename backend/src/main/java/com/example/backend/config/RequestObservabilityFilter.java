package com.example.backend.config;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestObservabilityFilter extends OncePerRequestFilter {

  public static final String REQUEST_ID_HEADER = "X-Request-ID";
  public static final String REQUEST_ID_ATTRIBUTE =
      RequestObservabilityFilter.class.getName() + ".requestId";

  private static final Logger LOGGER = LoggerFactory.getLogger(RequestObservabilityFilter.class);
  private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._:-]{1,100}");
  private static final String FINANCIALS_ROOT_ROUTE = "/api/v1/financials";
  private static final String FINANCIALS_RESTORE_ROUTE = "/api/v1/financials/restore";

  private final MeterRegistry meterRegistry;

  public RequestObservabilityFilter(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String requestId = requestId(request.getHeader(REQUEST_ID_HEADER));
    long startedAt = System.nanoTime();
    request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
    response.setHeader(REQUEST_ID_HEADER, requestId);

    try (MDC.MDCCloseable ignored = MDC.putCloseable("requestId", requestId)) {
      filterChain.doFilter(request, response);
    } finally {
      String route = route(request);
      long durationMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
      int status = response.getStatus();

      recordFinancialMetrics(request.getMethod(), route, status);
      LOGGER
          .atInfo()
          .addKeyValue("requestId", requestId)
          .addKeyValue("method", request.getMethod())
          .addKeyValue("route", route)
          .addKeyValue("status", status)
          .addKeyValue("durationMs", durationMillis)
          .log(
              "HTTP request completed requestId={} method={} route={} status={} durationMs={}",
              requestId,
              request.getMethod(),
              route,
              status,
              durationMillis);
    }
  }

  private void recordFinancialMetrics(String method, String route, int status) {
    if ("PUT".equals(method) && FINANCIALS_ROOT_ROUTE.equals(route)) {
      meterRegistry.counter("financials.snapshot.saves", "result", result(status)).increment();
    }

    if ("POST".equals(method) && FINANCIALS_RESTORE_ROUTE.equals(route)) {
      meterRegistry
          .counter("financials.snapshot.restores", "format", "json", "result", result(status))
          .increment();
    }
  }

  private String requestId(String candidate) {
    if (candidate != null && SAFE_REQUEST_ID.matcher(candidate).matches()) {
      return candidate;
    }
    return UUID.randomUUID().toString();
  }

  private String route(HttpServletRequest request) {
    Object routePattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    if (routePattern instanceof String pattern) {
      return pattern;
    }

    String requestUri = request.getRequestURI();
    if (FINANCIALS_ROOT_ROUTE.equals(requestUri) || FINANCIALS_RESTORE_ROUTE.equals(requestUri)) {
      return requestUri;
    }
    if (requestUri.startsWith(FINANCIALS_ROOT_ROUTE)) {
      return FINANCIALS_ROOT_ROUTE + "/**";
    }
    if (requestUri.startsWith("/actuator/")) {
      return "/actuator/**";
    }
    return "unmatched";
  }

  private String result(int status) {
    if (status == 409) {
      return "conflict";
    }
    if (status >= 200 && status < 400) {
      return "success";
    }
    return "failure";
  }
}

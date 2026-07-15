package com.example.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

class RequestObservabilityFilterTests {

  @Test
  void preservesSafeRequestIdAndRecordsSnapshotConflict() throws Exception {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    RequestObservabilityFilter filter = new RequestObservabilityFilter(meterRegistry);
    MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/v1/financials");
    MockHttpServletResponse response = new MockHttpServletResponse();
    request.addHeader(RequestObservabilityFilter.REQUEST_ID_HEADER, "web-safe-request-123");

    filter.doFilter(
        request,
        response,
        (servletRequest, servletResponse) -> {
          servletRequest.setAttribute(
              HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/financials");
          ((MockHttpServletResponse) servletResponse).setStatus(409);
        });

    assertThat(response.getHeader(RequestObservabilityFilter.REQUEST_ID_HEADER))
        .isEqualTo("web-safe-request-123");
    assertThat(
            meterRegistry
                .get("financials.snapshot.saves")
                .tag("result", "conflict")
                .counter()
                .count())
        .isEqualTo(1.0);
  }

  @Test
  void replacesUnsafeRequestIdAndRecordsSuccessfulSnapshotSave() throws Exception {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    RequestObservabilityFilter filter = new RequestObservabilityFilter(meterRegistry);
    MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/v1/financials");
    MockHttpServletResponse response = new MockHttpServletResponse();
    request.addHeader(RequestObservabilityFilter.REQUEST_ID_HEADER, "unsafe request\nvalue");

    filter.doFilter(
        request,
        response,
        (servletRequest, servletResponse) ->
            servletRequest.setAttribute(
                HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/financials"));

    assertThat(response.getHeader(RequestObservabilityFilter.REQUEST_ID_HEADER))
        .matches("[0-9a-f-]{36}")
        .isNotEqualTo("unsafe request\nvalue");
    assertThat(
            meterRegistry
                .get("financials.snapshot.saves")
                .tag("result", "success")
                .counter()
                .count())
        .isEqualTo(1.0);
  }

  @Test
  void recordsJsonRestoreOutcomeWithoutRequestData() throws Exception {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    RequestObservabilityFilter filter = new RequestObservabilityFilter(meterRegistry);

    filterRestore(filter, "/api/v1/financials/restore", 200);

    assertThat(
            meterRegistry
                .get("financials.snapshot.restores")
                .tags("format", "json", "result", "success")
                .counter()
                .count())
        .isEqualTo(1.0);
  }

  private void filterRestore(RequestObservabilityFilter filter, String route, int status)
      throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", route);
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(
        request,
        response,
        (servletRequest, servletResponse) -> {
          servletRequest.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, route);
          ((MockHttpServletResponse) servletResponse).setStatus(status);
        });
  }
}

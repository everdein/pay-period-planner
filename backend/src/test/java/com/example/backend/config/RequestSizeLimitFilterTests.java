package com.example.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestSizeLimitFilterTests {
  private RequestSizeLimitFilter filter(int limit) {
    FinancialsSecurityProperties properties = new FinancialsSecurityProperties();
    properties.setMaxRequestBytes(limit);
    return new RequestSizeLimitFilter(properties);
  }

  private MockHttpServletRequest request(String body, int declaredLength) {
    MockHttpServletRequest request =
        new MockHttpServletRequest() {
          @Override
          public long getContentLengthLong() {
            return declaredLength;
          }
        };
    request.setContent(body.getBytes(StandardCharsets.UTF_8));
    request.setCharacterEncoding(StandardCharsets.UTF_8.name());
    return request;
  }

  @Test
  void rejectsUnknownUnderstatedAndDeclaredOversizedBodiesBeforeDownstream() throws Exception {
    for (int length : new int[] {-1, 1, 5}) {
      MockHttpServletResponse response = new MockHttpServletResponse();
      AtomicBoolean called = new AtomicBoolean();
      filter(4).doFilter(request("12345", length), response, (req, res) -> called.set(true));
      assertThat(response.getStatus()).isEqualTo(413);
      assertThat(called).isFalse();
    }
  }

  @Test
  void acceptsExactLimitAndReplaysAllBytesIncludingUnknownLength() throws Exception {
    for (int length : new int[] {-1, 4}) {
      MockHttpServletResponse response = new MockHttpServletResponse();
      filter(4)
          .doFilter(
              request("test", length),
              response,
              (req, res) -> {
                assertThat(req.getInputStream().readAllBytes())
                    .isEqualTo("test".getBytes(StandardCharsets.UTF_8));
                assertThat(req.getInputStream().isFinished()).isTrue();
                assertThatThrownBy(req::getReader).isInstanceOf(IllegalStateException.class);
              });
      assertThat(response.getStatus()).isEqualTo(200);
    }
  }

  @Test
  void preservesReaderCharsetAndExclusivity() throws Exception {
    filter(4)
        .doFilter(
            request("é", -1),
            new MockHttpServletResponse(),
            (req, res) -> {
              assertThat(req.getReader().readLine()).isEqualTo("é");
              assertThatThrownBy(req::getInputStream).isInstanceOf(IllegalStateException.class);
            });
  }

  @Test
  void rejectsTrailingBytesBeforeAnyParserOrSideEffect() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter(2)
        .doFilter(
            request("{}   ", -1),
            response,
            (req, res) -> {
              throw new AssertionError("Oversized body must not reach a parser");
            });
    assertThat(response.getStatus()).isEqualTo(413);
  }

  @Test
  void acceptsEmptyBodiesAndDefaultReaderEncoding() throws Exception {
    MockHttpServletRequest request = request("", -1);
    request.setCharacterEncoding((String) null);
    filter(0)
        .doFilter(
            request,
            new MockHttpServletResponse(),
            (req, res) -> assertThat(req.getReader().read()).isEqualTo(-1));
  }

  @Test
  void countsShortReadsAndRejectsInvalidConfiguration() throws Exception {
    MockHttpServletRequest request =
        new MockHttpServletRequest() {
          private final jakarta.servlet.ServletInputStream input =
              new org.springframework.mock.web.DelegatingServletInputStream(
                  new java.io.ByteArrayInputStream(new byte[5]) {
                    @Override
                    public synchronized int read(byte[] bytes, int offset, int length) {
                      return super.read(bytes, offset, Math.min(1, length));
                    }
                  });

          @Override
          public long getContentLengthLong() {
            return -1;
          }

          @Override
          public jakarta.servlet.ServletInputStream getInputStream() {
            return input;
          }
        };
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter(4)
        .doFilter(
            request,
            response,
            (req, res) -> {
              throw new AssertionError("Short reads must still enforce the limit");
            });
    assertThat(response.getStatus()).isEqualTo(413);
    assertThatThrownBy(
            () ->
                filter(-1)
                    .doFilter(request("", -1), new MockHttpServletResponse(), (req, res) -> {}))
        .isInstanceOf(IllegalStateException.class);
  }
}

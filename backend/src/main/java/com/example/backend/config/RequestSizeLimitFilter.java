package com.example.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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

    if (maxRequestBytes < 0) {
      throw new IllegalStateException("The maximum request size must not be negative");
    }
    if (contentLength > maxRequestBytes) {
      reject(response);
      return;
    }

    ByteArrayOutputStream body = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    long bytesRead = 0;
    int count;
    while ((count = request.getInputStream().read(buffer)) != -1) {
      bytesRead += count;
      if (bytesRead > maxRequestBytes) {
        reject(response);
        return;
      }
      body.write(buffer, 0, count);
    }
    filterChain.doFilter(new BufferedRequest(request, body.toByteArray()), response);
  }

  private void reject(HttpServletResponse response) throws IOException {
    response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
    response.setContentType(MediaType.TEXT_PLAIN_VALUE);
    response.getWriter().write("Request body exceeds the configured maximum size");
  }

  private static final class BufferedRequest extends HttpServletRequestWrapper {
    private final ServletInputStream stream;
    private BufferedReader reader;
    private boolean streamRequested;

    BufferedRequest(HttpServletRequest request, byte[] body) {
      super(request);
      ByteArrayInputStream input = new ByteArrayInputStream(body);
      stream =
          new ServletInputStream() {
            @Override
            public int read() {
              return input.read();
            }

            @Override
            public int read(byte[] bytes, int offset, int length) {
              return input.read(bytes, offset, length);
            }

            @Override
            public boolean isFinished() {
              return input.available() == 0;
            }

            @Override
            public boolean isReady() {
              return true;
            }

            @Override
            public void setReadListener(ReadListener listener) {
              throw new IllegalStateException("Nonblocking request reads are not supported");
            }
          };
    }

    @Override
    public ServletInputStream getInputStream() {
      if (reader != null) throw new IllegalStateException("Reader already requested");
      streamRequested = true;
      return stream;
    }

    @Override
    public BufferedReader getReader() throws IOException {
      if (streamRequested) throw new IllegalStateException("Input stream already requested");
      if (reader == null) {
        String encoding = getCharacterEncoding();
        reader =
            new BufferedReader(
                new InputStreamReader(
                    stream, encoding == null ? StandardCharsets.ISO_8859_1.name() : encoding));
      }
      return reader;
    }
  }
}

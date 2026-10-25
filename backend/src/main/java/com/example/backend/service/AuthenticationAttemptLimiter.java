package com.example.backend.service;

import java.time.Clock;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Per-process, shared signup/signin budgets. Forwarding headers are never identity inputs. */
@Component
public class AuthenticationAttemptLimiter {
  private static final long WINDOW_MILLIS = 60_000;
  private static final int ACCOUNT_LIMIT = 10;
  private static final int SOURCE_LIMIT = 30;
  private static final int GLOBAL_LIMIT = 100;
  private static final int MAX_ACTIVE = 4;
  private static final int MAX_IDENTITIES = 10_000;
  private final Clock clock;
  private final Map<String, Window> accounts = new HashMap<>();
  private final Map<String, Window> sources = new HashMap<>();
  private Window global;
  private int active;

  public AuthenticationAttemptLimiter() {
    this(Clock.systemUTC());
  }

  AuthenticationAttemptLimiter(Clock clock) {
    this.clock = clock;
  }

  public synchronized Permit acquire(String email, String remoteAddress) {
    long now = clock.millis();
    accounts.values().removeIf(window -> window.expiresAt <= now);
    sources.values().removeIf(window -> window.expiresAt <= now);
    if (global == null || global.expiresAt <= now) global = new Window(now);
    String account = email.trim().toLowerCase(Locale.ROOT);
    String source = remoteAddress == null ? "unknown" : remoteAddress;
    if (active >= MAX_ACTIVE
        || global.attempts >= GLOBAL_LIMIT
        || accounts.size()
                + sources.size()
                + (accounts.containsKey(account) ? 0 : 1)
                + (sources.containsKey(source) ? 0 : 1)
            > MAX_IDENTITIES
        || exhausted(accounts.get(account), ACCOUNT_LIMIT)
        || exhausted(sources.get(source), SOURCE_LIMIT)) {
      throw new AuthenticationThrottledException();
    }
    accounts.computeIfAbsent(account, key -> new Window(now)).attempts++;
    sources.computeIfAbsent(source, key -> new Window(now)).attempts++;
    global.attempts++;
    active++;
    return new Permit();
  }

  private boolean exhausted(Window window, int limit) {
    return window != null && window.attempts >= limit;
  }

  private static final class Window {
    private final long expiresAt;
    private int attempts;

    Window(long now) {
      expiresAt = now + WINDOW_MILLIS;
    }
  }

  public final class Permit implements AutoCloseable {
    private boolean closed;

    private Permit() {}

    @Override
    public void close() {
      synchronized (AuthenticationAttemptLimiter.this) {
        if (!closed) {
          active--;
          closed = true;
        }
      }
    }
  }
}

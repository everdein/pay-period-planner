package com.example.backend.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AuthenticationAttemptLimiterTests {
  @Test
  void combinesNormalizedAccountAttemptsAcrossSourcesAndExpiresThem() {
    MutableClock clock = new MutableClock();
    AuthenticationAttemptLimiter limiter = new AuthenticationAttemptLimiter(clock);
    for (int index = 0; index < 10; index++) {
      limiter.acquire(" Owner@Example.com ", "source-" + index).close();
    }
    assertThatThrownBy(() -> limiter.acquire("owner@example.com", "new-source"))
        .isInstanceOf(AuthenticationThrottledException.class);
    clock.now = clock.now.plusSeconds(60);
    limiter.acquire("owner@example.com", "new-source").close();
  }

  @Test
  void limitsSourcesAcrossAccountsAndGlobalAttemptsAcrossSources() {
    AuthenticationAttemptLimiter limiter = new AuthenticationAttemptLimiter();
    for (int index = 0; index < 30; index++) {
      limiter.acquire("account-" + index, "same-source").close();
    }
    assertThatThrownBy(() -> limiter.acquire("different-account", "same-source"))
        .isInstanceOf(AuthenticationThrottledException.class);
    for (int index = 30; index < 100; index++) {
      limiter.acquire("account-" + index, "source-" + index).close();
    }
    assertThatThrownBy(() -> limiter.acquire("new-account", "new-source"))
        .isInstanceOf(AuthenticationThrottledException.class);
  }

  @Test
  void boundsConcurrentWorkAndReleasesExactlyOnceOnException() {
    AuthenticationAttemptLimiter limiter = new AuthenticationAttemptLimiter();
    var one = limiter.acquire("one", "source");
    var two = limiter.acquire("two", "source");
    var three = limiter.acquire("three", "source");
    var four = limiter.acquire("four", "source");
    assertThatThrownBy(() -> limiter.acquire("five", "source"))
        .isInstanceOf(AuthenticationThrottledException.class);
    one.close();
    one.close();
    try (var permit = limiter.acquire("five", "source")) {
      assertThatThrownBy(() -> limiter.acquire("six", "source"))
          .isInstanceOf(AuthenticationThrottledException.class);
    }
    assertThatThrownBy(
            () -> {
              try (var permit = limiter.acquire("six", "source")) {
                throw new IllegalStateException("Synthetic work failure");
              }
            })
        .isInstanceOf(IllegalStateException.class);
    limiter.acquire("seven", "source").close();
    two.close();
    three.close();
    four.close();
  }

  private static class MutableClock extends Clock {
    private Instant now = Instant.parse("2026-10-24T12:00:00Z");

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}

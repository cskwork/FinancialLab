package io.github.cskwork.finlab.auth.application;

import io.github.cskwork.finlab.common.error.ApiException;
import io.github.cskwork.finlab.config.FinLabProperties;
import java.time.*;
import java.util.ArrayList;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class LoginAttemptLimiterTest {
    private final MutableClock clock = new MutableClock();

    @Test void limitsIpAcrossDifferentEmailsIndependentlyOfTheEmailBuckets() {
        var limiter = limiter(2, 10, Duration.ofMinutes(1), 100);
        limiter.check("192.0.2.1", "first@example.com");
        limiter.check("192.0.2.1", "second@example.com");
        assertLimited(() -> limiter.check("192.0.2.1", "third@example.com"), 30);
        limiter.check("192.0.2.2", "third@example.com");
    }

    @Test void limitsNormalizedEmailAcrossDifferentIpAddresses() {
        var limiter = limiter(10, 2, Duration.ofMinutes(1), 100);
        limiter.check("192.0.2.1", " DEMO@example.com ");
        limiter.check("192.0.2.2", "demo@EXAMPLE.COM");
        assertLimited(() -> limiter.check("192.0.2.3", "demo@example.com"), 30);
        limiter.check("192.0.2.3", "other@example.com");
    }

    @Test void refillsOneTokenAtItsConfiguredRateAndNeverExceedsCapacity() {
        var limiter = limiter(2, 2, Duration.ofMinutes(1), 100);
        limiter.check("192.0.2.1", "demo@example.com");
        limiter.check("192.0.2.1", "demo@example.com");
        clock.advance(Duration.ofSeconds(15));
        assertLimited(() -> limiter.check("192.0.2.1", "demo@example.com"), 15);
        clock.advance(Duration.ofSeconds(15));
        limiter.check("192.0.2.1", "demo@example.com");
        assertLimited(() -> limiter.check("192.0.2.1", "demo@example.com"), 30);
        clock.advance(Duration.ofDays(1));
        limiter.check("192.0.2.1", "demo@example.com");
        limiter.check("192.0.2.1", "demo@example.com");
        assertLimited(() -> limiter.check("192.0.2.1", "demo@example.com"), 30);
    }

    @Test void boundsMemoryAndReclaimsInactiveKeysWithoutEvictingActiveBuckets() {
        var limiter = limiter(10, 10, Duration.ofMinutes(1), 2);
        limiter.check("192.0.2.1", "demo@example.com");
        for (int i = 0; i < 100; i++) {
            int index = i;
            assertLimited(() -> limiter.check("192.0.2." + (index + 2), "new" + index + "@example.com"), 60);
        }
        assertThat(limiter.trackedKeys()).isEqualTo(2);
        clock.advance(Duration.ofMinutes(1));
        limiter.check("192.0.2.2", "other@example.com");
        assertThat(limiter.trackedKeys()).isEqualTo(2);
    }

    @Test void handlesMissingAddressAndSubMillisecondRefillWithoutAnUnboundedBypass() {
        var limiter = limiter(1, 1, Duration.ofNanos(500), 100);
        limiter.check(null, "demo@example.com");
        assertLimited(() -> limiter.check(null, "demo@example.com"), 1);
        clock.advance(Duration.ofNanos(500));
        limiter.check(null, "demo@example.com");
    }

    @Test void backwardClockDoesNotRefillTokens() {
        var limiter = limiter(1, 1, Duration.ofMinutes(1), 100);
        limiter.check("192.0.2.1", "demo@example.com");
        clock.advance(Duration.ofSeconds(-30));
        assertLimited(() -> limiter.check("192.0.2.1", "demo@example.com"), 60);
        clock.advance(Duration.ofSeconds(30));
        assertLimited(() -> limiter.check("192.0.2.1", "demo@example.com"), 60);
    }

    @Test void concurrentAttemptsCannotSpendTheSameTokenTwice() throws Exception {
        var limiter = limiter(5, 5, Duration.ofDays(1), 100);
        try (var workers = Executors.newFixedThreadPool(10)) {
            var futures = new ArrayList<Future<Boolean>>();
            var start = new CountDownLatch(1);
            for (int i = 0; i < 20; i++) {
                futures.add(workers.submit(() -> {
                    start.await();
                    try { limiter.check("192.0.2.1", "demo@example.com"); return true; }
                    catch (ApiException limited) { assertThat(limited.status()).isEqualTo(429); return false; }
                }));
            }
            start.countDown();
            int successes = 0;
            for (var result : futures) if (result.get(5, TimeUnit.SECONDS)) successes++;
            assertThat(successes).isEqualTo(5);
        }
    }

    private LoginAttemptLimiter limiter(int ip, int email, Duration period, int keys) {
        return new LoginAttemptLimiter(new FinLabProperties(null, null,
            new FinLabProperties.Login(2, ip, email, period, keys, null), null), clock);
    }
    private static void assertLimited(Runnable action, long retryAfterSeconds) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class, error -> {
            assertThat(error.status()).isEqualTo(429);
            assertThat(error.code()).isEqualTo("AUTHENTICATION_RATE_LIMITED");
            assertThat(error.retryAfterSeconds()).isEqualTo(retryAfterSeconds);
        });
    }
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-28T00:00:00Z");
        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}

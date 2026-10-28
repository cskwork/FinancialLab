package io.github.cskwork.finlab.auth.application;

import io.github.cskwork.finlab.common.error.ApiException;
import io.github.cskwork.finlab.config.FinLabProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Per-process token buckets; limits and memory remain bounded under arbitrary account names. */
@Component
public class LoginAttemptLimiter {
    private final Clock clock;
    private final int ipCapacity;
    private final int emailCapacity;
    private final int maxTrackedKeys;
    private final Duration refillPeriod;
    private final Map<String, Bucket> buckets = new HashMap<>();
    private Instant nextCleanup = Instant.MIN;

    public LoginAttemptLimiter(FinLabProperties properties, Clock clock) {
        this.clock = clock;
        var settings = properties.login();
        this.ipCapacity = settings.ipCapacity();
        this.emailCapacity = settings.emailCapacity();
        this.maxTrackedKeys = settings.maxTrackedKeys();
        this.refillPeriod = settings.refillPeriod();
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public synchronized void check(String clientAddress, String email) {
        Instant now = clock.instant();
        if (!now.isBefore(nextCleanup)) {
            buckets.values().removeIf(bucket -> !now.isBefore(bucket.lastSeen.plus(refillPeriod)));
            nextCleanup = now.plus(refillPeriod);
        }
        String ipKey = "ip:" + (clientAddress == null ? "unknown" : clientAddress);
        String emailKey = "email:" + normalizeEmail(email);
        int additions = (buckets.containsKey(ipKey) ? 0 : 1) + (buckets.containsKey(emailKey) ? 0 : 1);
        if (buckets.size() + additions > maxTrackedKeys) {
            throw limited(seconds(durationSeconds(refillPeriod)));
        }
        Bucket ip = buckets.computeIfAbsent(ipKey, ignored -> new Bucket(ipCapacity, now));
        Bucket account = buckets.computeIfAbsent(emailKey, ignored -> new Bucket(emailCapacity, now));
        refill(ip, ipCapacity, now);
        refill(account, emailCapacity, now);
        if (ip.tokens < 1 || account.tokens < 1) {
            double waitSeconds = Math.max(waitSeconds(ip, ipCapacity), waitSeconds(account, emailCapacity));
            throw limited(seconds(waitSeconds));
        }
        ip.tokens--;
        account.tokens--;
    }

    private void refill(Bucket bucket, int capacity, Instant now) {
        double elapsed = Math.max(0, durationSeconds(Duration.between(bucket.updatedAt, now)));
        bucket.tokens = Math.min(capacity, bucket.tokens + elapsed * capacity / durationSeconds(refillPeriod));
        if (now.isAfter(bucket.updatedAt)) bucket.updatedAt = now;
        if (now.isAfter(bucket.lastSeen)) bucket.lastSeen = now;
    }

    private double waitSeconds(Bucket bucket, int capacity) {
        return bucket.tokens >= 1 ? 0 : (1 - bucket.tokens) * durationSeconds(refillPeriod) / capacity;
    }
    private static double durationSeconds(Duration duration) { return duration.getSeconds() + duration.getNano() / 1_000_000_000.0; }
    private static long seconds(double seconds) { return Math.max(1, (long) Math.ceil(seconds)); }
    private static ApiException limited(long retryAfter) {
        return new ApiException(429, "AUTHENTICATION_RATE_LIMITED", "로그인 요청이 너무 많습니다. 잠시 후 다시 시도하세요", retryAfter);
    }
    synchronized int trackedKeys() { return buckets.size(); }
    private static final class Bucket {
        private double tokens;
        private Instant updatedAt;
        private Instant lastSeen;
        private Bucket(int capacity, Instant now) { tokens = capacity; updatedAt = now; lastSeen = now; }
    }
}

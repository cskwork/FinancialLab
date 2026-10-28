package io.github.cskwork.finlab.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Operational limits are validated at startup, before serving requests. */
@Validated
@ConfigurationProperties("finlab")
public record FinLabProperties(@Valid Cors cors, @Valid Idempotency idempotency,
                              @Valid Login login, @Valid Ledger ledger) {
    public FinLabProperties {
        cors = cors == null ? new Cors(null) : cors;
        idempotency = idempotency == null ? new Idempotency(null, null) : idempotency;
        login = login == null ? new Login(2, 20, 10, null, 10000, null) : login;
        ledger = ledger == null ? new Ledger(3000, 1000) : ledger;
    }
    public static FinLabProperties defaults() { return new FinLabProperties(null, null, null, null); }

    public record Cors(@NotEmpty List<@NotBlank String> allowedOrigins) {
        public Cors { allowedOrigins = allowedOrigins == null ? List.of("http://localhost:5173") : List.copyOf(allowedOrigins); }
        @AssertTrue(message = "CORS origins must be explicit HTTP(S) origins")
        public boolean isOriginAllowlistValid() {
            return allowedOrigins.stream().allMatch(value -> {
                try {
                    var uri = URI.create(value);
                    return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) && uri.getHost() != null
                            && uri.getUserInfo() == null && uri.getRawQuery() == null && uri.getFragment() == null
                            && (uri.getPath() == null || uri.getPath().isEmpty());
                } catch (IllegalArgumentException failure) { return false; }
            });
        }
    }
    public record Idempotency(Duration ttl, Duration purgeInterval) {
        public Idempotency {
            ttl = ttl == null ? Duration.ofHours(24) : ttl;
            purgeInterval = purgeInterval == null ? Duration.ofHours(1) : purgeInterval;
        }
        @AssertTrue(message = "Idempotency durations must be positive")
        public boolean isDurationValid() { return positive(ttl) && positive(purgeInterval); }
    }
    public record Login(@DefaultValue("2") @Min(1) @Max(64) int hashingPermits,
                        @DefaultValue("20") @Min(1) int ipCapacity, @DefaultValue("10") @Min(1) int emailCapacity,
                        Duration refillPeriod, @DefaultValue("10000") @Min(2) @Max(1000000) int maxTrackedKeys,
                        Duration hashingWait) {
        public Login {
            refillPeriod = refillPeriod == null ? Duration.ofMinutes(1) : refillPeriod;
            hashingWait = hashingWait == null ? Duration.ofMillis(500) : hashingWait;
        }
        @AssertTrue(message = "Login refill period must be positive")
        public boolean isRefillValid() { return positive(refillPeriod); }
        @AssertTrue(message = "Login hashing wait must be positive and at most five seconds")
        public boolean isHashingWaitValid() { return positive(hashingWait) && hashingWait.compareTo(Duration.ofSeconds(5)) <= 0; }
    }
    public record Ledger(@DefaultValue("3000") @Min(1) @Max(60000) int lockTimeoutMs,
                         @DefaultValue("1000") @Min(1) @Max(10000) int transactionListLimit) { }

    private static boolean positive(Duration duration) { return !duration.isZero() && !duration.isNegative(); }
}

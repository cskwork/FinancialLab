package io.github.cskwork.finlab.transaction.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyPolicyTest {
    private static final Instant EXPIRES_AT = Instant.parse("2026-10-28T12:00:00Z");

    @ParameterizedTest(name = "now = expiry {0}ns, request hash = {1}: {2}")
    @CsvSource(textBlock = """
            -1, stored-hash, REPLAY
            -1, different-hash, MISMATCH
             0, stored-hash, EXPIRED
             0, different-hash, EXPIRED
             1, stored-hash, EXPIRED
             1, different-hash, EXPIRED
            """)
    void expirationTakesPrecedenceAtAndAfterTheTtlBoundary(
            long offsetNanos, String requestHash, IdempotencyPolicy.Decision expected) {
        var now = EXPIRES_AT.plusNanos(offsetNanos);

        assertThat(IdempotencyPolicy.evaluate("stored-hash", requestHash, EXPIRES_AT, now))
                .isEqualTo(expected);
    }
}

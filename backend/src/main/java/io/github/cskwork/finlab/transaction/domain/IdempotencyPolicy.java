package io.github.cskwork.finlab.transaction.domain;

import java.time.Instant;

/** A key is scoped to its portfolio, expires at its TTL, and binds one intent. */
public final class IdempotencyPolicy {
    private IdempotencyPolicy() { }
    public enum Decision { REPLAY, EXPIRED, MISMATCH }

    public static Decision evaluate(String storedHash, String requestHash, Instant expiresAt, Instant now) {
        if (!expiresAt.isAfter(now)) return Decision.EXPIRED;
        return storedHash.equals(requestHash) ? Decision.REPLAY : Decision.MISMATCH;
    }
}

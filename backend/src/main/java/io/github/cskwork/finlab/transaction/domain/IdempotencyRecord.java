package io.github.cskwork.finlab.transaction.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "idempotency_records", uniqueConstraints = @UniqueConstraint(columnNames = {"portfolio_id", "idempotency_key"}))
public class IdempotencyRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private long portfolioId;
    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String key;
    @Column(nullable = false, length = 64)
    private String requestHash;
    @Column(nullable = false, columnDefinition = "text")
    private String responseBody;
    @Column(nullable = false)
    private Instant expiresAt;

    protected IdempotencyRecord() { }
    public IdempotencyRecord(long portfolioId, String key, String requestHash, String responseBody, Instant expiresAt) {
        this.portfolioId = portfolioId;
        this.key = key;
        this.requestHash = requestHash;
        this.responseBody = responseBody;
        this.expiresAt = expiresAt;
    }
    public String getRequestHash() { return requestHash; }
    public String getResponseBody() { return responseBody; }
    public Instant getExpiresAt() { return expiresAt; }
}

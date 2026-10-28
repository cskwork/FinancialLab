package io.github.cskwork.finlab.portfolio.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "portfolios")
public class Portfolio {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private long userId;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(name = "base_currency", nullable = false, length = 3)
    private String baseCurrency;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected Portfolio() {}

    public Portfolio(long userId, String name, String baseCurrency, Instant now) {
        this.userId = userId;
        this.name = name;
        this.baseCurrency = baseCurrency;
        this.createdAt = now;
        this.updatedAt = createdAt;
    }

    public void update(String name, String baseCurrency, Instant now) {
        if (name != null) this.name = name;
        if (baseCurrency != null) this.baseCurrency = baseCurrency;
        updatedAt = now;
    }

    public Long getId() { return id; }
    public long getUserId() { return userId; }
    public String getName() { return name; }
    public String getBaseCurrency() { return baseCurrency; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

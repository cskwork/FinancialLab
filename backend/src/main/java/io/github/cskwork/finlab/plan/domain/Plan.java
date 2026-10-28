package io.github.cskwork.finlab.plan.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "plans")
public class Plan {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "portfolio_id", nullable = false)
    private long portfolioId;
    @Column(nullable = false)
    private String symbol;
    @Column(name = "amount_per_interval", nullable = false, precision = 19, scale = 4)
    private BigDecimal amountPerInterval;
    @Enumerated(EnumType.STRING)
    @Column(name = "interval", nullable = false)
    private PlanInterval interval;
    @Column(name = "start_date", nullable = false)
    private Instant startDate;
    @Column(name = "end_date")
    private Instant endDate;
    @Column(name = "is_active", nullable = false)
    private boolean active;

    protected Plan() {}
    public Plan(long portfolioId, String symbol, BigDecimal amountPerInterval, PlanInterval interval,
                Instant startDate, Instant endDate, boolean active) {
        this.portfolioId = portfolioId;
        this.symbol = symbol;
        this.amountPerInterval = amountPerInterval;
        this.interval = interval;
        this.startDate = startDate;
        this.endDate = endDate;
        this.active = active;
    }
    public Long getId() { return id; }
    public long getPortfolioId() { return portfolioId; }
    public String getSymbol() { return symbol; }
    public BigDecimal getAmountPerInterval() { return amountPerInterval; }
    public PlanInterval getInterval() { return interval; }
    public Instant getStartDate() { return startDate; }
    public Instant getEndDate() { return endDate; }
    public boolean isActive() { return active; }
}

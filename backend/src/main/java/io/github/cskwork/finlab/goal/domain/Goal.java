package io.github.cskwork.finlab.goal.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "goals")
public class Goal {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "portfolio_id", nullable = false)
    private long portfolioId;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(name = "target_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal targetAmount;
    @Column(name = "target_date", nullable = false)
    private Instant targetDate;
    @Column(name = "current_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal currentAmount;

    protected Goal() {}
    public Goal(long portfolioId, String name, BigDecimal targetAmount, Instant targetDate, BigDecimal currentAmount) {
        this.portfolioId = portfolioId;
        this.name = name;
        this.targetAmount = targetAmount;
        this.targetDate = targetDate;
        this.currentAmount = currentAmount;
    }
    public Long getId() { return id; }
    public long getPortfolioId() { return portfolioId; }
    public String getName() { return name; }
    public BigDecimal getTargetAmount() { return targetAmount; }
    public Instant getTargetDate() { return targetDate; }
    public BigDecimal getCurrentAmount() { return currentAmount; }
}

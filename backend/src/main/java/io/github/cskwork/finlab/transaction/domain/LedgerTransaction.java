package io.github.cskwork.finlab.transaction.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/** Append-only ledger row; corrections delete and revalidate the remaining ledger. */
@Entity
@Table(name = "ledger_transactions")
public class LedgerTransaction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private long portfolioId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10)
    private TransactionType type;
    @Column(length = 32)
    private String symbol;
    @Column(precision = 20, scale = 8)
    private BigDecimal qty;
    @Column(precision = 19, scale = 4)
    private BigDecimal price;
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal fee;
    @Column(nullable = false)
    private Instant timestamp;
    @Column(length = 500)
    private String note;

    protected LedgerTransaction() { }

    public LedgerTransaction(long portfolioId, LedgerEntry entry, String note) {
        this.portfolioId = portfolioId;
        type = entry.type();
        symbol = entry.symbol();
        // Match PostgreSQL's persisted scales before creating a response or storing its replay.
        qty = scaled(entry.qty(), 8);
        price = scaled(entry.price(), 4);
        fee = scaled(entry.fee(), 4);
        timestamp = entry.timestamp();
        this.note = note;
    }

    private static BigDecimal scaled(BigDecimal value, int scale) {
        return value == null ? null : value.setScale(scale, RoundingMode.UNNECESSARY);
    }

    public LedgerEntry toEntry() { return new LedgerEntry(type, symbol, qty, price, fee, timestamp); }
    public Long getId() { return id; }
    public long getPortfolioId() { return portfolioId; }
    public TransactionType getType() { return type; }
    public String getSymbol() { return symbol; }
    public BigDecimal getQty() { return qty; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getFee() { return fee; }
    public Instant getTimestamp() { return timestamp; }
    public String getNote() { return note; }
}

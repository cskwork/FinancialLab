package io.github.cskwork.finlab.marketdata.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.hibernate.annotations.Immutable;

/** Immutable daily OHLCV imported from the same bundled files as the frontend demo. */
@Entity
@Immutable
@Table(name = "candles")
public class Candle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 32)
    private String symbol;
    @Column(nullable = false)
    private LocalDate date;
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal open;
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal high;
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal low;
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal close;
    @Column(nullable = false)
    private long volume;

    protected Candle() {
    }

    public Candle(String symbol, LocalDate date, BigDecimal open, BigDecimal high,
                  BigDecimal low, BigDecimal close, long volume) {
        this.symbol = symbol;
        this.date = date;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
    }

    public Long getId() { return id; }
    public String getSymbol() { return symbol; }
    public LocalDate getDate() { return date; }
    public BigDecimal getOpen() { return open; }
    public BigDecimal getHigh() { return high; }
    public BigDecimal getLow() { return low; }
    public BigDecimal getClose() { return close; }
    public long getVolume() { return volume; }
}

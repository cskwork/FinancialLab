package io.github.cskwork.finlab.metrics.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.cskwork.finlab.common.error.ApiException;
import io.github.cskwork.finlab.holding.application.HoldingService;
import io.github.cskwork.finlab.holding.application.HoldingService.HoldingView;
import io.github.cskwork.finlab.holding.application.HoldingService.Valuation;
import io.github.cskwork.finlab.marketdata.domain.Candle;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MetricsServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-28T03:00:00Z");
    private final HoldingService holdings = mock(HoldingService.class);
    private final MetricsService service = new MetricsService(holdings, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void matchesPercentReturnsPopulationVarianceAndFrontendPnlFormula() {
        when(holdings.valuation(3, 7)).thenReturn(new Valuation(List.of(holding("AAPL", "100"), holding("SPY", "100")),
                Map.of("AAPL", List.of(candle("AAPL", "2024-01-01", "100"), candle("AAPL", "2024-01-02", "104"),
                        candle("AAPL", "2024-01-03", "104")),
                        "SPY", List.of(candle("SPY", "2024-01-01", "100"), candle("SPY", "2024-01-02", "100"),
                        candle("SPY", "2024-01-03", "100")))));

        var snapshot = service.metrics(3, 7);

        // Market-value weights are 1/2, so the aggregate daily returns are [2%, 0%].
        assertThat(snapshot.totalValue()).isEqualByComparingTo("200");
        assertThat(snapshot.cash()).isZero();
        assertThat(snapshot.pnlDaily()).isZero();
        assertThat(snapshot.pnlYTD()).isEqualByComparingTo("4");
        assertThat(snapshot.volatility()).isCloseTo(Math.sqrt(252), offset(1e-12));
        assertThat(snapshot.sharpe()).isCloseTo((1 - 2.0 / 252) * Math.sqrt(252), offset(1e-12));
        assertThat(snapshot.maxDrawdown()).isZero();
        assertThat(snapshot.portfolioId()).isEqualTo(3);
        assertThat(snapshot.id()).isZero();
        assertThat(snapshot.asOf()).isEqualTo(NOW);
    }

    @Test
    void emptyPortfolioReturnsZeroSnapshot() {
        when(holdings.valuation(3, 7)).thenReturn(new Valuation(List.of(), Map.of()));

        assertZeroSnapshot(service.metrics(3, 7));
    }

    @Test
    void zeroMarketValueReturnsZeroSnapshot() {
        when(holdings.valuation(3, 7)).thenReturn(new Valuation(List.of(holding("AAPL", "0")), Map.of()));

        assertZeroSnapshot(service.metrics(3, 7));
    }

    @Test
    void noPriceHistoryKeepsValuationAndZeroRiskMetrics() {
        when(holdings.valuation(3, 7)).thenReturn(new Valuation(List.of(holding("UNKNOWN", "100")), Map.of()));

        var snapshot = service.metrics(3, 7);

        assertThat(snapshot.totalValue()).isEqualByComparingTo("100");
        assertThat(snapshot.pnlDaily()).isZero();
        assertThat(snapshot.pnlYTD()).isZero();
        assertThat(snapshot.volatility()).isZero();
        assertThat(snapshot.sharpe()).isZero();
        assertThat(snapshot.maxDrawdown()).isZero();
    }

    @Test
    void mergesUnionOfDatesAndSkipsZeroPreviousClose() {
        when(holdings.valuation(3, 7)).thenReturn(new Valuation(List.of(holding("AAPL", "100"), holding("SPY", "100")),
                Map.of("AAPL", List.of(candle("AAPL", "2024-01-01", "0"), candle("AAPL", "2024-01-02", "100"),
                        candle("AAPL", "2024-01-03", "90")),
                        "SPY", List.of(candle("SPY", "2024-01-01", "100"), candle("SPY", "2024-01-02", "104")))));

        var snapshot = service.metrics(3, 7);

        // Union of dates gives [+2%, -5%], with a 5% drawdown from the intervening peak.
        assertThat(snapshot.pnlDaily().doubleValue()).isCloseTo(-10, offset(1e-12));
        assertThat(snapshot.pnlYTD().doubleValue()).isCloseTo(-6.2, offset(1e-12));
        assertThat(snapshot.maxDrawdown()).isCloseTo(5, offset(1e-12));
    }

    @Test
    void matchesBundledDemoSharpeExactlyAsTheFrontendSpec() throws Exception {
        var aapl = readCandles("AAPL");
        var spy = readCandles("SPY");
        when(holdings.valuation(3, 7)).thenReturn(new Valuation(
                List.of(holding("AAPL", "19176.3"), holding("SPY", "12754")), Map.of("AAPL", aapl, "SPY", spy)));

        var snapshot = service.metrics(3, 7);

        assertThat(snapshot.totalValue()).isEqualByComparingTo("31930.3");
        assertThat(snapshot.sharpe()).isCloseTo(5.4106641762, offset(1e-10));
    }

    @Test
    void foreignPortfolioCannotExposeMetrics() {
        when(holdings.valuation(3, 7)).thenThrow(ApiException.notFound());

        assertThatThrownBy(() -> service.metrics(3, 7)).isInstanceOf(ApiException.class);
    }

    private static void assertZeroSnapshot(MetricsService.Snapshot snapshot) {
        assertThat(snapshot.totalValue()).isZero();
        assertThat(snapshot.cash()).isZero();
        assertThat(snapshot.pnlDaily()).isZero();
        assertThat(snapshot.pnlYTD()).isZero();
        assertThat(snapshot.volatility()).isZero();
        assertThat(snapshot.sharpe()).isZero();
        assertThat(snapshot.maxDrawdown()).isZero();
    }

    private static HoldingView holding(String symbol, String marketValue) {
        return new HoldingView(1, 3, symbol, BigDecimal.ONE, BigDecimal.ONE,
                new BigDecimal(marketValue), BigDecimal.ZERO, Instant.EPOCH);
    }

    private static Candle candle(String symbol, String date, String close) {
        var price = new BigDecimal(close);
        return new Candle(symbol, LocalDate.parse(date), price, price, price, price, 100);
    }

    private static List<Candle> readCandles(String symbol) throws Exception {
        try (var lines = Files.lines(Path.of("../frontend/public/data/ohlcv/" + symbol + ".csv"))) {
            return lines.skip(1).filter(line -> !line.isBlank()).map(line -> {
                var fields = line.split(",");
                return new Candle(symbol, LocalDate.parse(fields[0]), new BigDecimal(fields[1]),
                        new BigDecimal(fields[2]), new BigDecimal(fields[3]), new BigDecimal(fields[4]),
                        Long.parseLong(fields[5]));
            }).toList();
        }
    }
}

package io.github.cskwork.finlab.holding.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.cskwork.finlab.common.error.ApiException;
import io.github.cskwork.finlab.marketdata.application.MarketDataService;
import io.github.cskwork.finlab.marketdata.domain.Candle;
import io.github.cskwork.finlab.transaction.application.TransactionService;
import io.github.cskwork.finlab.transaction.domain.LedgerEntry;
import io.github.cskwork.finlab.transaction.domain.TransactionType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HoldingServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-28T03:00:00Z");
    private final TransactionService transactions = mock(TransactionService.class);
    private final MarketDataService marketData = mock(MarketDataService.class);
    private final HoldingService service = new HoldingService(transactions, marketData, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void valuesFeeInclusiveLotsAtLatestClose() {
        when(transactions.ledger(3, 7)).thenReturn(List.of(trade(TransactionType.BUY, "AAPL", "10", "100", "2")));
        var old = candle("AAPL", "2024-01-01", "110");
        var latest = candle("AAPL", "2024-01-02", "120");
        when(marketData.histories(anyCollection())).thenReturn(Map.of("AAPL", List.of(old, latest)));

        var holding = service.holdings(3, 7).getFirst();

        assertThat(holding.id()).isEqualTo(1);
        assertThat(holding.portfolioId()).isEqualTo(3);
        assertThat(holding.symbol()).isEqualTo("AAPL");
        assertThat(holding.shares()).isEqualByComparingTo("10");
        assertThat(holding.avgCost()).isEqualByComparingTo("100.2");
        assertThat(holding.marketValue()).isEqualByComparingTo("1200");
        assertThat(holding.unrealizedPnL()).isEqualByComparingTo("198");
        assertThat(holding.lastUpdated()).isEqualTo(NOW);
    }

    @Test
    void missingMarketDataFallsBackToAverageCostWithoutProfit() {
        when(transactions.ledger(3, 7)).thenReturn(List.of(trade(TransactionType.BUY, "NEW", "3", "100", "1")));
        when(marketData.histories(anyCollection())).thenReturn(Map.of());

        var holding = service.holdings(3, 7).getFirst();

        assertThat(holding.marketValue().doubleValue()).isCloseTo(301, org.assertj.core.data.Offset.offset(1e-10));
        assertThat(holding.unrealizedPnL()).isZero();
    }

    @Test
    void fullySoldPositionIsAbsent() {
        when(transactions.ledger(3, 7)).thenReturn(List.of(
                trade(TransactionType.BUY, "AAPL", "10", "100", "0"),
                trade(TransactionType.SELL, "AAPL", "10", "110", "0")));
        when(marketData.histories(anyCollection())).thenReturn(Map.of());

        assertThat(service.holdings(3, 7)).isEmpty();
    }

    @Test
    void emptyPortfolioHasNoHoldings() {
        when(transactions.ledger(3, 7)).thenReturn(List.of());
        when(marketData.histories(anyCollection())).thenReturn(Map.of());

        assertThat(service.valuation(3, 7).holdings()).isEmpty();
    }

    @Test
    void foreignPortfolioIsRejectedBeforeMarketDataIsRead() {
        when(transactions.ledger(3, 7)).thenThrow(ApiException.notFound());

        assertThatThrownBy(() -> service.holdings(3, 7)).isInstanceOf(ApiException.class);
        verifyNoInteractions(marketData);
    }

    private static LedgerEntry trade(TransactionType type, String symbol, String qty, String price, String fee) {
        return new LedgerEntry(type, symbol, new BigDecimal(qty), new BigDecimal(price), new BigDecimal(fee),
                Instant.parse("2024-01-01T00:00:00Z"));
    }

    private static Candle candle(String symbol, String date, String close) {
        var price = new BigDecimal(close);
        return new Candle(symbol, LocalDate.parse(date), price, price, price, price, 100);
    }
}

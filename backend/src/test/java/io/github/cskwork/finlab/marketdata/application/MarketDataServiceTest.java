package io.github.cskwork.finlab.marketdata.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.cskwork.finlab.common.error.ApiException;
import io.github.cskwork.finlab.marketdata.domain.Candle;
import io.github.cskwork.finlab.marketdata.persistence.CandleRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarketDataServiceTest {
    private final CandleRepository repository = mock(CandleRepository.class);
    private final MarketDataService service = new MarketDataService(repository);

    @Test
    void defaultsToTheFullSupportedDateRange() {
        var candle = candle("AAPL", "2024-01-02", "185.64");
        when(repository.findBySymbolAndDateBetweenOrderByDateAsc("AAPL", LocalDate.of(1, 1, 1), LocalDate.of(9999, 12, 31)))
                .thenReturn(List.of(candle));

        assertThat(service.candles("AAPL", null, null)).containsExactly(candle);
    }

    @Test
    void passesInclusiveDateBoundsToTheRepository() {
        var day = LocalDate.of(2024, 1, 2);
        when(repository.findBySymbolAndDateBetweenOrderByDateAsc("AAPL", day, day)).thenReturn(List.of());

        assertThat(service.candles("AAPL", day, day)).isEmpty();
        verify(repository).findBySymbolAndDateBetweenOrderByDateAsc("AAPL", day, day);
    }

    @Test
    void rejectsReversedDateBoundsBeforeQuerying() {
        assertThatThrownBy(() -> service.candles("AAPL", LocalDate.of(2024, 2, 1), LocalDate.of(2024, 1, 1)))
                .isInstanceOf(ApiException.class).hasMessageContaining("startDate");
        verifyNoInteractions(repository);
    }

    @Test
    void loadsAllHistoriesWithOneQueryAndKeepsDateOrder() {
        var first = candle("AAPL", "2024-01-02", "185.64");
        var second = candle("AAPL", "2024-01-03", "184.25");
        var spy = candle("SPY", "2024-01-02", "476.24");
        when(repository.findBySymbolInOrderBySymbolAscDateAsc(List.of("AAPL", "SPY")))
                .thenReturn(List.of(first, second, spy));

        var histories = service.histories(List.of("AAPL", "SPY"));

        assertThat(histories.get("AAPL")).containsExactly(first, second);
        assertThat(histories.get("SPY")).containsExactly(spy);
        verify(repository).findBySymbolInOrderBySymbolAscDateAsc(List.of("AAPL", "SPY"));
        assertThat(first.getId()).isNull();
        assertThat(first.getOpen()).isEqualByComparingTo("185.64");
        assertThat(first.getHigh()).isEqualByComparingTo("185.64");
        assertThat(first.getLow()).isEqualByComparingTo("185.64");
        assertThat(first.getVolume()).isEqualTo(100);
    }

    @Test
    void emptyHoldingsDoNotQueryMarketData() {
        assertThat(service.histories(List.of())).isEmpty();
        verifyNoInteractions(repository);
    }

    private static Candle candle(String symbol, String date, String close) {
        var price = new BigDecimal(close);
        return new Candle(symbol, LocalDate.parse(date), price, price, price, price, 100);
    }
}

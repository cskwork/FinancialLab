package io.github.cskwork.finlab.marketdata.application;

import io.github.cskwork.finlab.common.error.ApiException;
import io.github.cskwork.finlab.marketdata.domain.Candle;
import io.github.cskwork.finlab.marketdata.persistence.CandleRepository;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MarketDataService {
    private static final LocalDate FIRST_DATE = LocalDate.of(1, 1, 1);
    private static final LocalDate LAST_DATE = LocalDate.of(9999, 12, 31);
    private final CandleRepository repository;

    public MarketDataService(CandleRepository repository) {
        this.repository = repository;
    }

    public List<Candle> candles(String symbol, LocalDate startDate, LocalDate endDate) {
        var start = startDate == null ? FIRST_DATE : startDate;
        var end = endDate == null ? LAST_DATE : endDate;
        if (start.isAfter(end)) {
            throw ApiException.validation("startDate는 endDate보다 늦을 수 없습니다");
        }
        return repository.findBySymbolAndDateBetweenOrderByDateAsc(symbol, start, end);
    }

    /** One query for every held symbol; callers do not trigger an OHLCV query per holding. */
    public Map<String, List<Candle>> histories(Collection<String> symbols) {
        if (symbols.isEmpty()) {
            return Map.of();
        }
        var histories = new LinkedHashMap<String, List<Candle>>();
        repository.findBySymbolInOrderBySymbolAscDateAsc(symbols)
                .forEach(candle -> histories.computeIfAbsent(candle.getSymbol(), ignored -> new java.util.ArrayList<>())
                        .add(candle));
        return histories;
    }
}

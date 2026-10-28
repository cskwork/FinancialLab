package io.github.cskwork.finlab.marketdata.persistence;

import io.github.cskwork.finlab.marketdata.domain.Candle;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandleRepository extends JpaRepository<Candle, Long> {
    List<Candle> findBySymbolAndDateBetweenOrderByDateAsc(String symbol, LocalDate start, LocalDate end);

    List<Candle> findBySymbolInOrderBySymbolAscDateAsc(Collection<String> symbols);
}

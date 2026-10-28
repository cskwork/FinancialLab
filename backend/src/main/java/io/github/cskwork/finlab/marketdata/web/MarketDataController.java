package io.github.cskwork.finlab.marketdata.web;

import io.github.cskwork.finlab.marketdata.application.MarketDataService;
import io.github.cskwork.finlab.marketdata.domain.Candle;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MarketDataController {
    private final MarketDataService service;

    public MarketDataController(MarketDataService service) {
        this.service = service;
    }

    @Operation(operationId = "getMarketData", summary = "Read bundled daily OHLCV", description = "Returns a symbol's daily candles, ordered by date. Date bounds are inclusive.")
    @GetMapping("/v1/market-data")
    public List<CandleResponse> candles(@RequestParam String symbol,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return service.candles(symbol, startDate, endDate).stream().map(CandleResponse::from).toList();
    }

    @Schema(name = "CandleResponse", description = "A daily open, high, low, close and volume observation from the bundled demo dataset.")
    public record CandleResponse(String symbol, LocalDate date, BigDecimal open, BigDecimal high,
                                 BigDecimal low, BigDecimal close, long volume) {
        static CandleResponse from(Candle candle) {
            return new CandleResponse(candle.getSymbol(), candle.getDate(), candle.getOpen(), candle.getHigh(),
                    candle.getLow(), candle.getClose(), candle.getVolume());
        }
    }
}

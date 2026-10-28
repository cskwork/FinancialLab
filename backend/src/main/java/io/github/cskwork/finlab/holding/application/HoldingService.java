package io.github.cskwork.finlab.holding.application;

import io.github.cskwork.finlab.marketdata.application.MarketDataService;
import io.github.cskwork.finlab.marketdata.domain.Candle;
import io.github.cskwork.finlab.transaction.application.TransactionService;
import io.github.cskwork.finlab.transaction.domain.FifoLedger;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class HoldingService {
    private final TransactionService transactions;
    private final MarketDataService marketData;
    private final Clock clock;

    public HoldingService(TransactionService transactions, MarketDataService marketData, Clock clock) {
        this.transactions = transactions;
        this.marketData = marketData;
        this.clock = clock;
    }

    public List<HoldingView> holdings(long portfolioId, long userId) {
        return valuation(portfolioId, userId).holdings();
    }

    /** Shares one consistent FIFO valuation and batch-loaded history with metrics computation. */
    public Valuation valuation(long portfolioId, long userId) {
        // Reading the ledger enforces ownership, including portfolios with no transactions.
        var positions = FifoLedger.replay(transactions.ledger(portfolioId, userId)).holdings();
        var histories = marketData.histories(positions.keySet());
        var holdings = new ArrayList<HoldingView>();
        var lastUpdated = clock.instant();
        long id = 1;
        for (var position : positions.values()) {
            var candles = histories.getOrDefault(position.symbol(), List.of());
            var latestPrice = candles.isEmpty() ? position.avgCost() : candles.getLast().getClose();
            var marketValue = position.shares().multiply(latestPrice);
            var costBasis = position.shares().multiply(position.avgCost());
            holdings.add(new HoldingView(id++, portfolioId, position.symbol(), position.shares(),
                    position.avgCost(), marketValue, marketValue.subtract(costBasis), lastUpdated));
        }
        return new Valuation(List.copyOf(holdings), histories);
    }

    public record HoldingView(long id, long portfolioId, String symbol, BigDecimal shares, BigDecimal avgCost,
                              BigDecimal marketValue, BigDecimal unrealizedPnL, Instant lastUpdated) {
    }

    public record Valuation(List<HoldingView> holdings, Map<String, List<Candle>> histories) {
    }
}

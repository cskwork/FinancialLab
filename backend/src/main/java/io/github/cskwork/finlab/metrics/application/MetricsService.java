package io.github.cskwork.finlab.metrics.application;

import io.github.cskwork.finlab.holding.application.HoldingService;
import io.github.cskwork.finlab.metrics.domain.MetricsCalculator;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MetricsService {
    private static final MathContext PRECISION = MathContext.DECIMAL128;
    private final HoldingService holdings;
    private final Clock clock;

    public MetricsService(HoldingService holdings, Clock clock) {
        this.holdings = holdings;
        this.clock = clock;
    }

    public Snapshot metrics(long portfolioId, long userId) {
        var valuation = holdings.valuation(portfolioId, userId);
        var totalValue = valuation.holdings().stream().map(HoldingService.HoldingView::marketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (valuation.holdings().isEmpty() || totalValue.signum() == 0) {
            return new Snapshot(0, portfolioId, clock.instant(), BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, 0);
        }

        var returnsByDate = new TreeMap<LocalDate, Double>();
        for (var holding : valuation.holdings()) {
            double weight = holding.marketValue().doubleValue() / totalValue.doubleValue();
            var candles = valuation.histories().getOrDefault(holding.symbol(), List.of());
            for (int i = 1; i < candles.size(); i++) {
                var previous = candles.get(i - 1);
                var current = candles.get(i);
                if (previous.getClose().signum() == 0) {
                    continue;
                }
                double dailyReturn = (current.getClose().doubleValue() - previous.getClose().doubleValue())
                        / previous.getClose().doubleValue() * 100;
                returnsByDate.merge(current.getDate(), dailyReturn * weight, Double::sum);
            }
        }
        var returns = List.copyOf(returnsByDate.values());
        var calculated = returns.isEmpty() ? new MetricsCalculator.Result(0, 0, 0, 0, 0)
                : MetricsCalculator.calculate(returns, 0.02);
        var pnlDaily = returns.isEmpty() ? BigDecimal.ZERO
                : BigDecimal.valueOf(returns.getLast() / 100).multiply(totalValue, PRECISION);
        var cumulativeReturn = BigDecimal.ONE;
        for (var dailyReturn : returns) {
            cumulativeReturn = cumulativeReturn.multiply(BigDecimal.ONE.add(BigDecimal.valueOf(dailyReturn / 100)), PRECISION);
        }
        var pnlYtd = cumulativeReturn.subtract(BigDecimal.ONE).multiply(totalValue, PRECISION);
        return new Snapshot(0, portfolioId, clock.instant(), totalValue, BigDecimal.ZERO, pnlDaily,
                pnlYtd, calculated.volatility(), calculated.sharpe(), calculated.maxDrawdown());
    }

    public record Snapshot(long id, long portfolioId, Instant asOf, BigDecimal totalValue, BigDecimal cash,
                           BigDecimal pnlDaily, BigDecimal pnlYTD, double volatility, double sharpe, double maxDrawdown) {
    }
}

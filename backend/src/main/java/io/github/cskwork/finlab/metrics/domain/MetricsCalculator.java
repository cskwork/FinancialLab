package io.github.cskwork.finlab.metrics.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

/** Frontend-compatible metrics: daily returns are percentages; the annual risk-free rate is decimal. */
public final class MetricsCalculator {
    private static final double TRADING_DAYS = 252;
    private static final MathContext PRECISION = MathContext.DECIMAL128;

    private MetricsCalculator() {
    }

    public record Result(double sharpe, double volatility, double maxDrawdown,
                         double avgReturn, double stdDev) {
    }

    public static Result calculate(List<Double> returns, double riskFreeRate) {
        if (returns.isEmpty()) {
            throw new IllegalArgumentException("No returns data");
        }
        if (!Double.isFinite(riskFreeRate)
                || returns.stream().anyMatch(value -> value == null || !Double.isFinite(value))) {
            throw new IllegalArgumentException("Returns and risk-free rate must be finite");
        }
        // Keep the same left-to-right arithmetic as the JavaScript reference.
        double sum = 0;
        for (var dailyReturn : returns) {
            sum += dailyReturn;
        }
        double mean = sum / returns.size();
        double squaredDifferences = 0;
        for (var dailyReturn : returns) {
            double difference = dailyReturn - mean;
            squaredDifferences += difference * difference;
        }
        double stdDev = Math.sqrt(squaredDifferences / returns.size());
        double volatility = stdDev * Math.sqrt(TRADING_DAYS);
        double dailyRiskFreeRate = riskFreeRate * 100 / TRADING_DAYS;
        double sharpe = stdDev == 0 ? 0 : (mean - dailyRiskFreeRate) / stdDev * Math.sqrt(TRADING_DAYS);

        var peak = BigDecimal.ONE;
        var cumulativeReturn = BigDecimal.ONE;
        var maxDrawdown = BigDecimal.ZERO;
        for (var dailyReturn : returns) {
            cumulativeReturn = cumulativeReturn.multiply(
                    BigDecimal.ONE.add(BigDecimal.valueOf(dailyReturn / 100)), PRECISION);
            peak = peak.max(cumulativeReturn);
            var drawdown = peak.subtract(cumulativeReturn).divide(peak, PRECISION)
                    .multiply(BigDecimal.valueOf(100));
            maxDrawdown = maxDrawdown.max(drawdown);
        }
        return new Result(sharpe, volatility, maxDrawdown.doubleValue(), mean, stdDev);
    }
}

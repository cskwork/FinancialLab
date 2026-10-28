package io.github.cskwork.finlab.metrics.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

class MetricsCalculatorTest {
    @Test
    void annualDecimalRiskFreeRateIsConvertedToDailyPercent() {
        var metrics = MetricsCalculator.calculate(List.of(2.0, 0.0), 0.252);
        // Mean 1%, population deviation 1%; annual 25.2% becomes 0.1% per trading day.
        assertThat(metrics.avgReturn()).isEqualTo(1);
        assertThat(metrics.stdDev()).isEqualTo(1);
        assertThat(metrics.volatility()).isEqualTo(Math.sqrt(252));
        assertThat(metrics.sharpe()).isCloseTo(0.9 * Math.sqrt(252), within(1e-12));
        assertThat(metrics.maxDrawdown()).isZero();
    }

    @Test
    void twoPercentAnnualRiskFreeRateUsesPercentReturnUnits() {
        var metrics = MetricsCalculator.calculate(List.of(2.0, 0.0), 0.02);
        assertThat(metrics.sharpe()).isCloseTo((1 - 2.0 / 252) * Math.sqrt(252), within(1e-12));
    }

    @Test
    void emptySeriesIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> MetricsCalculator.calculate(List.of(), 0.02))
                .withMessage("No returns data");
    }

    @Test
    void constantSeriesHasZeroVolatilityAndSharpe() {
        var metrics = MetricsCalculator.calculate(List.of(1.0, 1.0, 1.0), 0.02);
        assertThat(metrics.avgReturn()).isEqualTo(1);
        assertThat(metrics.stdDev()).isZero();
        assertThat(metrics.volatility()).isZero();
        assertThat(metrics.sharpe()).isZero();
    }

    @Test
    void singleNegativeReturnHasDrawdownFromInitialEquity() {
        var metrics = MetricsCalculator.calculate(List.of(-20.0), 0.02);
        assertThat(metrics.sharpe()).isZero();
        assertThat(metrics.maxDrawdown()).isEqualTo(20);
    }

    @Test
    void drawdownCompoundsAndResetsPeakAfterRecovery() {
        var metrics = MetricsCalculator.calculate(List.of(10.0, -10.0, -20.0, 50.0, -5.0), 0.02);
        // Equity 1 -> 1.1 -> .99 -> .792: decline from 1.1 is 28%, not 30%.
        assertThat(metrics.maxDrawdown()).isCloseTo(28, within(1e-12));
    }

    @Test
    void totalLossProducesOneHundredPercentDrawdown() {
        assertThat(MetricsCalculator.calculate(List.of(10.0, -100.0, 20.0), 0.02).maxDrawdown())
                .isEqualTo(100);
    }

    @Test
    void standardDeviationUsesThePopulationDenominator() {
        var metrics = MetricsCalculator.calculate(List.of(-2.0, 2.0), 0);
        assertThat(metrics.avgReturn()).isZero();
        assertThat(metrics.stdDev()).isEqualTo(2);
        assertThat(metrics.volatility()).isEqualTo(2 * Math.sqrt(252));
        assertThat(metrics.sharpe()).isZero();
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void nonFiniteInputsCannotReachJsonResponses(double value) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MetricsCalculator.calculate(List.of(value), 0.02))
                .withMessageContaining("must be finite");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MetricsCalculator.calculate(List.of(1.0), value))
                .withMessageContaining("must be finite");
    }

    @Test
    void nullReturnsAreRejectedExplicitly() {
        var returns = new ArrayList<Double>();
        returns.add(null);
        assertThatIllegalArgumentException().isThrownBy(() -> MetricsCalculator.calculate(returns, 0.02))
                .withMessageContaining("must be finite");
    }

    @Test
    void bundledPricesAndLatestValueWeightsMatchFrontendSharpe() throws Exception {
        var returnsByDate = new TreeMap<String, Double>();
        for (var position : List.of(new Position("AAPL", 19176.3), new Position("SPY", 12754))) {
            var lines = Files.readAllLines(Path.of("..", "frontend", "public", "data", "ohlcv",
                    position.symbol() + ".csv"));
            for (int i = 2; i < lines.size(); i++) {
                var previous = lines.get(i - 1).split(",");
                var current = lines.get(i).split(",");
                double previousClose = Double.parseDouble(previous[4]);
                double currentClose = Double.parseDouble(current[4]);
                double weightedReturn = (currentClose - previousClose) / previousClose * 100
                        * position.value() / 31930.3;
                returnsByDate.merge(current[0], weightedReturn, Double::sum);
            }
        }
        assertThat(returnsByDate).hasSize(61);
        var metrics = MetricsCalculator.calculate(new ArrayList<>(returnsByDate.values()), 0.02);
        assertThat(metrics.sharpe()).isCloseTo(5.4106641762, within(1e-10));
    }

    private record Position(String symbol, double value) {
    }
}

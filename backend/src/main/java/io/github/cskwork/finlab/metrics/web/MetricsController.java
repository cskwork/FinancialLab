package io.github.cskwork.finlab.metrics.web;

import io.github.cskwork.finlab.common.web.ApiNumbers;
import io.github.cskwork.finlab.common.web.CurrentUserId;
import io.github.cskwork.finlab.metrics.application.MetricsService;
import io.github.cskwork.finlab.metrics.application.MetricsService.Snapshot;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MetricsController {
    private final MetricsService service;

    public MetricsController(MetricsService service) {
        this.service = service;
    }

    @Operation(operationId = "getPortfolioMetrics", summary = "Compute portfolio risk metrics", description = "Matches the frontend's latest-market-value weighted daily percent returns, population volatility, 252-day annualization and 2% annual risk-free rate. Cash is zero under this contract.")
    @GetMapping("/v1/metrics/portfolio/{portfolioId}")
    public MetricSnapshotResponse metrics(@PathVariable long portfolioId, @CurrentUserId long userId) {
        return MetricSnapshotResponse.from(service.metrics(portfolioId, userId));
    }

    @Schema(name = "MetricSnapshotResponse", description = "On-demand metrics over bundled price history. Volatility and maxDrawdown are percentages; Sharpe is a ratio. pnlYTD follows the frontend's full-history compounded-return formula.")
    public record MetricSnapshotResponse(long id, long portfolioId, Instant asOf, BigDecimal totalValue,
                                         BigDecimal cash, BigDecimal pnlDaily, BigDecimal pnlYTD,
                                         double volatility, double sharpe, double maxDrawdown) {
        static MetricSnapshotResponse from(Snapshot snapshot) {
            return new MetricSnapshotResponse(snapshot.id(), snapshot.portfolioId(), snapshot.asOf(),
                    ApiNumbers.money(snapshot.totalValue()), ApiNumbers.money(snapshot.cash()),
                    ApiNumbers.money(snapshot.pnlDaily()), ApiNumbers.money(snapshot.pnlYTD()),
                    snapshot.volatility(), snapshot.sharpe(), snapshot.maxDrawdown());
        }
    }
}

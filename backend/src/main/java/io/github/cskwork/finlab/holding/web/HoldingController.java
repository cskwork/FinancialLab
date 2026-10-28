package io.github.cskwork.finlab.holding.web;

import io.github.cskwork.finlab.common.web.ApiNumbers;
import io.github.cskwork.finlab.common.web.CurrentUserId;
import io.github.cskwork.finlab.holding.application.HoldingService;
import io.github.cskwork.finlab.holding.application.HoldingService.HoldingView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HoldingController {
    private final HoldingService service;

    public HoldingController(HoldingService service) {
        this.service = service;
    }

    @Operation(operationId = "getHoldings", summary = "Read FIFO holdings", description = "Values remaining fee-inclusive FIFO lots at the latest bundled closing price, falling back to average cost when no price exists.")
    @GetMapping("/v1/portfolios/{portfolioId}/holdings")
    public List<HoldingResponse> holdings(@PathVariable long portfolioId, @CurrentUserId long userId) {
        return service.holdings(portfolioId, userId).stream().map(HoldingResponse::from).toList();
    }

    @Schema(name = "HoldingResponse", description = "Current FIFO position and its unrealized profit or loss. Values use the portfolio's base currency without FX conversion.")
    public record HoldingResponse(long id, long portfolioId, String symbol, BigDecimal shares, BigDecimal avgCost,
                                  BigDecimal marketValue, BigDecimal unrealizedPnL, Instant lastUpdated) {
        static HoldingResponse from(HoldingView holding) {
            return new HoldingResponse(holding.id(), holding.portfolioId(), holding.symbol(),
                    ApiNumbers.quantity(holding.shares()), ApiNumbers.derived(holding.avgCost()),
                    ApiNumbers.money(holding.marketValue()), ApiNumbers.money(holding.unrealizedPnL()), holding.lastUpdated());
        }
    }
}

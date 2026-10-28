package io.github.cskwork.finlab.plan.web;

import io.github.cskwork.finlab.common.error.ApiException;
import io.github.cskwork.finlab.common.web.CurrentUserId;
import io.github.cskwork.finlab.plan.application.PlanService;
import io.github.cskwork.finlab.plan.domain.Plan;
import io.github.cskwork.finlab.plan.domain.PlanInterval;
import io.github.cskwork.finlab.plan.web.validation.ValidPlanDates;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;

@RestController
@Tag(name = "Plans", description = "Recurring portfolio investment plans")
public class PlanController {
    private final PlanService service;
    public PlanController(PlanService service) { this.service = service; }

    @GetMapping("/v1/portfolios/{portfolioId}/plans")
    @Operation(summary = "List owned portfolio investment plans")
    public List<PlanResponse> list(@PathVariable long portfolioId, @CurrentUserId long userId) {
        return service.list(portfolioId, userId).stream().map(PlanResponse::from).toList();
    }
    @PostMapping("/v1/portfolios/{portfolioId}/plans")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a weekly or monthly investment plan")
    public PlanResponse create(@PathVariable long portfolioId, @Valid @RequestBody CreatePlanRequest request,
                               @CurrentUserId long userId) {
        if (portfolioId != request.portfolioId()) {
            throw new ApiException(400, "VALIDATION_ERROR", "portfolioId must match the path");
        }
        return PlanResponse.from(service.create(portfolioId, userId,
                new PlanService.CreatePlan(request.portfolioId(), request.symbol(), request.amountPerInterval(), request.interval(),
                        request.startDate(), request.endDate(), request.isActive())));
    }
    @DeleteMapping("/v1/plans/{id}")
    @Operation(summary = "Delete an owned investment plan")
    public Map<String, Boolean> delete(@PathVariable long id, @CurrentUserId long userId) {
        service.delete(id, userId);
        return Map.of("success", true);
    }
    @Schema(name = "CreatePlanRequest", description = "Investment frequency, amount and optional end date; active defaults to true")
    @ValidPlanDates
    public record CreatePlanRequest(@NotNull Long portfolioId, @NotBlank @Size(max = 32) String symbol,
                                    @NotNull @Positive @Digits(integer = 15, fraction = 4) BigDecimal amountPerInterval,
                                    @NotNull PlanInterval interval, @NotNull Instant startDate,
                                    Instant endDate, Boolean isActive) {}
    @Schema(name = "PlanResponse", description = "A recurring investment plan with UTC bounds")
    public record PlanResponse(long id, long portfolioId, String symbol, BigDecimal amountPerInterval,
                               PlanInterval interval, Instant startDate, Instant endDate, boolean isActive) {
        static PlanResponse from(Plan p) {
            return new PlanResponse(p.getId(), p.getPortfolioId(), p.getSymbol(), p.getAmountPerInterval(), p.getInterval(),
                    p.getStartDate(), p.getEndDate(), p.isActive());
        }
    }
}

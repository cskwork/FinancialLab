package io.github.cskwork.finlab.goal.web;

import io.github.cskwork.finlab.common.error.ApiException;
import io.github.cskwork.finlab.common.web.CurrentUserId;
import io.github.cskwork.finlab.goal.application.GoalService;
import io.github.cskwork.finlab.goal.domain.Goal;
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
@Tag(name = "Goals", description = "Portfolio saving goals")
public class GoalController {
    private final GoalService service;
    public GoalController(GoalService service) { this.service = service; }

    @GetMapping("/v1/portfolios/{portfolioId}/goals")
    @Operation(summary = "List owned portfolio goals")
    public List<GoalResponse> list(@PathVariable long portfolioId, @CurrentUserId long userId) {
        return service.list(portfolioId, userId).stream().map(GoalResponse::from).toList();
    }
    @PostMapping("/v1/portfolios/{portfolioId}/goals")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a saving goal")
    public GoalResponse create(@PathVariable long portfolioId, @Valid @RequestBody CreateGoalRequest request,
                               @CurrentUserId long userId) {
        if (portfolioId != request.portfolioId()) {
            throw new ApiException(400, "VALIDATION_ERROR", "portfolioId must match the path");
        }
        return GoalResponse.from(service.create(portfolioId, userId,
                new GoalService.CreateGoal(request.portfolioId(), request.name(), request.targetAmount(), request.targetDate(), request.currentAmount())));
    }
    @DeleteMapping("/v1/goals/{id}")
    @Operation(summary = "Delete an owned goal")
    public Map<String, Boolean> delete(@PathVariable long id, @CurrentUserId long userId) {
        service.delete(id, userId);
        return Map.of("success", true);
    }
    @Schema(name = "CreateGoalRequest", description = "Target amount/date and optional progress (defaults to zero)")
    public record CreateGoalRequest(@NotNull Long portfolioId, @NotBlank @Size(max = 100) String name,
                                    @NotNull @Positive @Digits(integer = 15, fraction = 4) BigDecimal targetAmount,
                                    @NotNull Instant targetDate,
                                    @PositiveOrZero @Digits(integer = 15, fraction = 4) BigDecimal currentAmount) {}
    @Schema(name = "GoalResponse", description = "A portfolio saving goal with UTC target date")
    public record GoalResponse(long id, long portfolioId, String name, BigDecimal targetAmount,
                               Instant targetDate, BigDecimal currentAmount) {
        static GoalResponse from(Goal g) {
            return new GoalResponse(g.getId(), g.getPortfolioId(), g.getName(), g.getTargetAmount(), g.getTargetDate(), g.getCurrentAmount());
        }
    }
}

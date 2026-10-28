package io.github.cskwork.finlab.portfolio.web;

import io.github.cskwork.finlab.common.web.CurrentUserId;
import io.github.cskwork.finlab.portfolio.application.PortfolioService;
import io.github.cskwork.finlab.portfolio.domain.Portfolio;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/v1/portfolios")
@Tag(name = "Portfolios", description = "User-owned portfolio aggregates")
public class PortfolioController {
    private final PortfolioService service;
    public PortfolioController(PortfolioService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "List the authenticated user's portfolios")
    public List<PortfolioResponse> list(@RequestParam long userId, @CurrentUserId long currentUserId) {
        return service.list(userId, currentUserId).stream().map(PortfolioResponse::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Read one owned portfolio")
    public PortfolioResponse get(@PathVariable long id, @CurrentUserId long userId) {
        return PortfolioResponse.from(service.requireOwned(id, userId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an owned portfolio")
    public PortfolioResponse create(@Valid @RequestBody CreatePortfolioRequest request,
                                    @CurrentUserId long userId) {
        return PortfolioResponse.from(service.create(request.userId(), userId, request.name(), request.baseCurrency()));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update a portfolio name or currency")
    public PortfolioResponse update(@PathVariable long id, @Valid @RequestBody UpdatePortfolioRequest request,
                                    @CurrentUserId long userId) {
        return PortfolioResponse.from(service.update(id, userId, request.name(), request.baseCurrency()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an owned portfolio and dependent resources")
    public Map<String, Boolean> delete(@PathVariable long id, @CurrentUserId long userId) {
        service.delete(id, userId);
        return Map.of("success", true);
    }

    @Schema(name = "CreatePortfolioRequest", description = "Portfolio creation input; userId must match the bearer subject")
    public record CreatePortfolioRequest(@NotNull Long userId, @NotBlank @Size(max = 100) String name,
                                         @Size(min = 3, max = 3) String baseCurrency) {}
    @Schema(name = "UpdatePortfolioRequest", description = "Optional portfolio fields to replace")
    public record UpdatePortfolioRequest(@Size(min = 1, max = 100) String name,
                                         @Size(min = 3, max = 3) String baseCurrency) {}
    @Schema(name = "PortfolioResponse", description = "Portfolio identity, owner and UTC creation/update times")
    public record PortfolioResponse(long id, long userId, String name, String baseCurrency,
                                    Instant createdAt, Instant updatedAt) {
        static PortfolioResponse from(Portfolio p) {
            return new PortfolioResponse(p.getId(), p.getUserId(), p.getName(), p.getBaseCurrency(), p.getCreatedAt(), p.getUpdatedAt());
        }
    }
}

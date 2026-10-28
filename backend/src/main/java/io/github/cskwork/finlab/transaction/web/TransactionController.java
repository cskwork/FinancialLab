package io.github.cskwork.finlab.transaction.web;

import io.github.cskwork.finlab.common.error.ApiException;
import io.github.cskwork.finlab.common.web.CurrentUserId;
import io.github.cskwork.finlab.transaction.application.TransactionService;
import io.github.cskwork.finlab.transaction.application.TransactionService.TransactionView;
import io.github.cskwork.finlab.transaction.domain.TransactionType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1")
public class TransactionController {
    private final TransactionService service;
    public TransactionController(TransactionService service) { this.service = service; }

    @Schema(name = "CreateTransaction", description = "A ledger entry. BUY and SELL require symbol, qty and price. Optional fee defaults to zero.")
    public record CreateTransaction(@Positive long portfolioId, @NotNull TransactionType type,
                                    @Size(max = 32) String symbol,
                                    @Positive @Digits(integer = 12, fraction = 8) BigDecimal qty,
                                    @Positive @Digits(integer = 15, fraction = 4) BigDecimal price,
                                    @PositiveOrZero @Digits(integer = 15, fraction = 4) BigDecimal fee,
                                    @NotNull Instant timestamp, @Size(max = 500) String note) {
        @AssertTrue(message = "BUY/SELL 거래는 symbol, qty, price가 필수입니다")
        public boolean isTradeComplete() {
            return type != TransactionType.BUY && type != TransactionType.SELL
                    || symbol != null && !symbol.isEmpty() && qty != null && price != null;
        }
        TransactionService.Command command() {
            return new TransactionService.Command(portfolioId, type, symbol, qty, price, fee, timestamp, note);
        }
    }

    @GetMapping("/portfolios/{portfolioId}/transactions")
    @Operation(summary = "List transactions", description = "Newest matching transactions first, bounded by the configured limit. X-Total-Count reports all matching rows before the limit.")
    public ResponseEntity<List<TransactionView>> list(@PathVariable long portfolioId, @CurrentUserId long userId,
                                      @RequestParam(required = false) Instant startDate,
                                      @RequestParam(required = false) Instant endDate,
                                      @RequestParam(required = false) TransactionType type,
                                      @RequestParam(required = false) String symbol) {
        var page = service.list(portfolioId, userId, startDate, endDate, type, symbol);
        return ResponseEntity.ok().header("X-Total-Count", Long.toString(page.getTotalElements()))
                .body(page.getContent());
    }

    @PostMapping("/portfolios/{portfolioId}/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create transaction", description = "Locks the portfolio and validates the entire FIFO ledger. Idempotency-Key replays an identical request for 24 hours by default; reuse with a different body returns 422.")
    public TransactionView create(@PathVariable long portfolioId, @CurrentUserId long userId,
                                   @Valid @RequestBody CreateTransaction request,
                                   @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        if (request.portfolioId() != portfolioId) throw new ApiException(400, "VALIDATION_ERROR", "Path and body portfolioId must match");
        return service.create(userId, request.command(), key);
    }

    @DeleteMapping("/transactions/{id}")
    @Operation(summary = "Delete transaction", description = "Refuses deletion if a subsequent SELL depends on the transaction. Returns JSON for the existing frontend client.")
    public Map<String, Boolean> delete(@PathVariable long id, @CurrentUserId long userId) {
        service.delete(id, userId);
        return Map.of("success", true);
    }
}

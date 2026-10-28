package io.github.cskwork.finlab.transaction.domain;

import java.math.BigDecimal;
import java.time.Instant;

/** An immutable ledger input; cash transfers have no symbol, quantity, or price. */
public record LedgerEntry(
        TransactionType type,
        String symbol,
        BigDecimal qty,
        BigDecimal price,
        BigDecimal fee,
        Instant timestamp) {
}

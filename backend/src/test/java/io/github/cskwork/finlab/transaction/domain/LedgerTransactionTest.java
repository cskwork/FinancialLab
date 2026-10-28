package io.github.cskwork.finlab.transaction.domain;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerTransactionTest {
    @Test
    void createdRowUsesDatabaseScalesBeforeItCanBecomeAnApiResponse() {
        var entry = new LedgerEntry(TransactionType.BUY, "AAPL", new BigDecimal("10"),
                new BigDecimal("100"), new BigDecimal("1"), Instant.EPOCH);

        var row = new LedgerTransaction(2, entry, "Exact persistence");

        assertThat(row.getQty()).isEqualTo(new BigDecimal("10.00000000"));
        assertThat(row.getPrice()).isEqualTo(new BigDecimal("100.0000"));
        assertThat(row.getFee()).isEqualTo(new BigDecimal("1.0000"));
        assertThat(row.toEntry()).isEqualTo(new LedgerEntry(TransactionType.BUY, "AAPL",
                row.getQty(), row.getPrice(), row.getFee(), Instant.EPOCH));
        assertThat(row.getNote()).isEqualTo("Exact persistence");
        assertThat(row.getPortfolioId()).isEqualTo(2);
    }

    @Test
    void precisionIsNeverSilentlyRoundedAtThePersistenceBoundary() {
        var entry = new LedgerEntry(TransactionType.BUY, "AAPL", BigDecimal.ONE,
                new BigDecimal("100.00001"), BigDecimal.ZERO, Instant.EPOCH);

        assertThatThrownBy(() -> new LedgerTransaction(2, entry, null)).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void transfersRetainAbsentOptionalFieldsAndZeroFeeScale() {
        var row = new LedgerTransaction(2,
                new LedgerEntry(TransactionType.DEPOSIT, null, null, null, BigDecimal.ZERO, Instant.EPOCH), null);

        assertThat(row.getQty()).isNull();
        assertThat(row.getPrice()).isNull();
        assertThat(row.getSymbol()).isNull();
        assertThat(row.getFee()).isEqualTo(new BigDecimal("0.0000"));
    }
}

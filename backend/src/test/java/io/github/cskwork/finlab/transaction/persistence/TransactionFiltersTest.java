package io.github.cskwork.finlab.transaction.persistence;

import io.github.cskwork.finlab.transaction.domain.LedgerTransaction;
import io.github.cskwork.finlab.transaction.domain.TransactionType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class TransactionFiltersTest {
    private static final Instant START = Instant.parse("2024-01-01T00:00:00Z");
    private static final Instant END = Instant.parse("2024-01-02T00:00:00Z");
    @SuppressWarnings("unchecked")
    private final Root<LedgerTransaction> root = mock(Root.class);
    @SuppressWarnings("unchecked")
    private final Path<Long> portfolio = mock(Path.class);
    @SuppressWarnings("unchecked")
    private final Path<Instant> timestamp = mock(Path.class);
    @SuppressWarnings("unchecked")
    private final Path<TransactionType> type = mock(Path.class);
    @SuppressWarnings("unchecked")
    private final Path<String> symbol = mock(Path.class);
    private final CriteriaBuilder builder = mock(CriteriaBuilder.class);

    @BeforeEach
    void paths() {
        when(root.<Long>get("portfolioId")).thenReturn(portfolio);
        when(root.<Instant>get("timestamp")).thenReturn(timestamp);
        when(root.<TransactionType>get("type")).thenReturn(type);
        when(root.<String>get("symbol")).thenReturn(symbol);
    }

    @Test
    void evenUnfilteredQueriesAreRestrictedToTheirPortfolio() {
        TransactionFilters.matching(2, null, null, null, null).toPredicate(root, null, builder);

        verify(builder).equal(portfolio, 2L);
        verify(builder).and(any(Predicate[].class));
        verifyNoMoreInteractions(builder);
    }

    @Test
    void lowerTimestampBoundaryIsInclusive() {
        TransactionFilters.matching(2, START, null, null, null).toPredicate(root, null, builder);

        verify(builder).greaterThanOrEqualTo(timestamp, START);
        verify(builder, never()).lessThanOrEqualTo(any(), any(Instant.class));
    }

    @Test
    void upperTimestampBoundaryIsInclusive() {
        TransactionFilters.matching(2, null, END, null, null).toPredicate(root, null, builder);

        verify(builder).lessThanOrEqualTo(timestamp, END);
        verify(builder, never()).greaterThanOrEqualTo(any(), any(Instant.class));
    }

    @Test
    void tradeTypeIsComparedAsItsEnumValue() {
        TransactionFilters.matching(2, null, null, TransactionType.SELL, null).toPredicate(root, null, builder);

        verify(builder).equal(type, TransactionType.SELL);
    }

    @Test
    void symbolsUseExactEqualityWithoutCaseConversion() {
        TransactionFilters.matching(2, null, null, null, "aApL").toPredicate(root, null, builder);

        verify(builder).equal(symbol, "aApL");
    }

    @Test
    void allRequestedFiltersBecomeOneConjunction() {
        var ownership = mock(Predicate.class);
        var lowerBoundary = mock(Predicate.class);
        var upperBoundary = mock(Predicate.class);
        var requestedType = mock(Predicate.class);
        var requestedSymbol = mock(Predicate.class);
        when(builder.equal(portfolio, 2L)).thenReturn(ownership);
        when(builder.greaterThanOrEqualTo(timestamp, START)).thenReturn(lowerBoundary);
        when(builder.lessThanOrEqualTo(timestamp, END)).thenReturn(upperBoundary);
        when(builder.equal(type, TransactionType.BUY)).thenReturn(requestedType);
        when(builder.equal(symbol, "AAPL")).thenReturn(requestedSymbol);

        TransactionFilters.matching(2, START, END, TransactionType.BUY, "AAPL").toPredicate(root, null, builder);

        verify(builder).equal(portfolio, 2L);
        verify(builder).greaterThanOrEqualTo(timestamp, START);
        verify(builder).lessThanOrEqualTo(timestamp, END);
        verify(builder).equal(type, TransactionType.BUY);
        verify(builder).equal(symbol, "AAPL");
        verify(builder).and(ownership, lowerBoundary, upperBoundary, requestedType, requestedSymbol);
    }
}

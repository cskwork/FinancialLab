package io.github.cskwork.finlab.transaction.persistence;

import io.github.cskwork.finlab.transaction.domain.LedgerTransaction;
import io.github.cskwork.finlab.transaction.domain.TransactionType;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import org.springframework.data.jpa.domain.Specification;

/** Criteria keep optional values typed and apply filters before the database row limit. */
public final class TransactionFilters {
    private TransactionFilters() { }

    public static Specification<LedgerTransaction> matching(long portfolioId, Instant startDate, Instant endDate,
                                                           TransactionType type, String symbol) {
        return (root, query, builder) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(builder.equal(root.get("portfolioId"), portfolioId));
            if (startDate != null) predicates.add(builder.greaterThanOrEqualTo(root.get("timestamp"), startDate));
            if (endDate != null) predicates.add(builder.lessThanOrEqualTo(root.get("timestamp"), endDate));
            if (type != null) predicates.add(builder.equal(root.get("type"), type));
            if (symbol != null) predicates.add(builder.equal(root.get("symbol"), symbol));
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}

package io.github.cskwork.finlab.transaction.persistence;

import io.github.cskwork.finlab.transaction.domain.LedgerTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import io.github.cskwork.finlab.transaction.domain.TransactionType;
import java.time.Instant;
import java.util.List;

public interface TransactionRepository extends JpaRepository<LedgerTransaction, Long>, JpaSpecificationExecutor<LedgerTransaction> {
    List<LedgerTransaction> findByPortfolioIdOrderByTimestampAscIdAsc(long portfolioId);

    default Page<LedgerTransaction> findFiltered(long portfolioId, Instant startDate, Instant endDate,
                                                TransactionType type, String symbol, Pageable pageable) {
        return findAll(TransactionFilters.matching(portfolioId, startDate, endDate, type, symbol), pageable);
    }
}

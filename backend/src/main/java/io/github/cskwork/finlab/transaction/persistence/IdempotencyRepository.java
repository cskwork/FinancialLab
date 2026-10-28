package io.github.cskwork.finlab.transaction.persistence;

import io.github.cskwork.finlab.transaction.domain.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;

public interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, Long> {
    Optional<IdempotencyRecord> findByPortfolioIdAndKey(long portfolioId, String key);

    @Modifying(flushAutomatically = true)
    @Query("delete from IdempotencyRecord r where r.expiresAt <= :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);

    @Modifying(flushAutomatically = true)
    @Query("delete from IdempotencyRecord r where r.portfolioId = :portfolioId and r.key = :key and r.expiresAt <= :cutoff")
    int deleteExpiredKey(@Param("portfolioId") long portfolioId, @Param("key") String key, @Param("cutoff") Instant cutoff);
}

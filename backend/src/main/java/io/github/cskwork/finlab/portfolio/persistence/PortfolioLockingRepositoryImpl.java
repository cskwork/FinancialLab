package io.github.cskwork.finlab.portfolio.persistence;

import io.github.cskwork.finlab.config.FinLabProperties;
import io.github.cskwork.finlab.portfolio.domain.Portfolio;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class PortfolioLockingRepositoryImpl implements PortfolioLockingRepository {
    private final EntityManager entityManager;
    private final FinLabProperties properties;

    public PortfolioLockingRepositoryImpl(EntityManager entityManager, FinLabProperties properties) {
        this.entityManager = entityManager;
        this.properties = properties;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Portfolio> lockOwned(long id, long userId) {
        int timeoutMs = properties.ledger().lockTimeoutMs();
        // PostgreSQL does not consistently honor positive JPA lock hints; scope the server limit to this transaction.
        entityManager.createNativeQuery("select set_config('lock_timeout', :timeout, true)")
                .setParameter("timeout", timeoutMs + "ms")
                .getSingleResult();
        return entityManager.createQuery("select p from Portfolio p where p.id = :id and p.userId = :userId", Portfolio.class)
                .setParameter("id", id)
                .setParameter("userId", userId)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .setHint("jakarta.persistence.lock.timeout", timeoutMs)
                .getResultList().stream().findFirst();
    }
}

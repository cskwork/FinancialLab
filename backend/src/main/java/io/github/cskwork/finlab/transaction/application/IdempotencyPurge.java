package io.github.cskwork.finlab.transaction.application;

import io.github.cskwork.finlab.transaction.persistence.IdempotencyRepository;
import java.time.Clock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Removes expired replay responses even when clients never reuse their keys. */
@Component
public class IdempotencyPurge {
    private final IdempotencyRepository repository;
    private final Clock clock;

    public IdempotencyPurge(IdempotencyRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${finlab.idempotency.purge-interval:1h}")
    @Transactional
    public void purgeExpired() {
        repository.deleteExpiredBefore(clock.instant());
    }
}

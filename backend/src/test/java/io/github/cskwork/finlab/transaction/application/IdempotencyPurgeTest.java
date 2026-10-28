package io.github.cskwork.finlab.transaction.application;

import io.github.cskwork.finlab.transaction.persistence.IdempotencyRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class IdempotencyPurgeTest {
    private final IdempotencyRepository repository = mock(IdempotencyRepository.class);
    private final Instant now = Instant.parse("2026-10-28T12:00:00Z");
    private final IdempotencyPurge purge = new IdempotencyPurge(repository, Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void scheduledRunDeletesExpiredRecordsUsingTheInjectedClock() {
        when(repository.deleteExpiredBefore(now)).thenReturn(3);

        purge.purgeExpired();

        verify(repository).deleteExpiredBefore(now);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void databaseFailureIsNotReportedAsSuccessfulCleanup() {
        var failure = new IllegalStateException("Database unavailable");
        when(repository.deleteExpiredBefore(now)).thenThrow(failure);

        assertThatThrownBy(purge::purgeExpired).isSameAs(failure);
    }
}

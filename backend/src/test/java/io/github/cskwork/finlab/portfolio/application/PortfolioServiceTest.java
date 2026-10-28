package io.github.cskwork.finlab.portfolio.application;

import io.github.cskwork.finlab.common.error.ApiException;
import io.github.cskwork.finlab.portfolio.domain.Portfolio;
import io.github.cskwork.finlab.portfolio.persistence.PortfolioRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {
    @Mock PortfolioRepository portfolios;
    private static final Instant NOW = Instant.parse("2026-10-28T00:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    PortfolioService service;
    @BeforeEach void setUp() { service = new PortfolioService(portfolios, clock); }

    @Test void listsOnlyTheRequestedOwner() {
        Portfolio portfolio = new Portfolio(7, "Long term", "USD", NOW.minusSeconds(1));
        when(portfolios.findByUserIdOrderByIdAsc(7)).thenReturn(List.of(portfolio));
        assertThat(service.list(7, 7)).containsExactly(portfolio);
    }
    @Test void anotherUserCannotEnumeratePortfolios() {
        assertThatThrownBy(() -> service.list(8, 7)).isInstanceOf(ApiException.class);
        verifyNoInteractions(portfolios);
    }
    @Test void existingOwnedPortfolioCanBeRead() {
        Portfolio portfolio = new Portfolio(7, "Long term", "USD", NOW.minusSeconds(1));
        when(portfolios.findByIdAndUserId(2, 7)).thenReturn(Optional.of(portfolio));
        assertThat(service.requireOwned(2, 7)).isSameAs(portfolio);
    }
    @Test void missingAndUnownedPortfolioUseTheSameFailure() {
        when(portfolios.findByIdAndUserId(2, 7)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.requireOwned(2, 7)).isInstanceOf(ApiException.class);
    }
    @Test void locksTheOwnedRowForLedgerSerialization() {
        Portfolio portfolio = new Portfolio(7, "Long term", "USD", NOW.minusSeconds(1));
        when(portfolios.lockOwned(2, 7)).thenReturn(Optional.of(portfolio));
        assertThat(service.lockOwned(2, 7)).isSameAs(portfolio);
        verify(portfolios).lockOwned(2, 7);
    }
    @Test void cannotLockAnotherUsersPortfolio() {
        when(portfolios.lockOwned(2, 7)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.lockOwned(2, 7)).isInstanceOf(ApiException.class);
    }
    @Test void createsWithTheAuthenticatedOwnerAndDefaultCurrency() {
        when(portfolios.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Portfolio created = service.create(7, 7, "Savings", null);
        assertThat(created.getUserId()).isEqualTo(7);
        assertThat(created.getName()).isEqualTo("Savings");
        assertThat(created.getBaseCurrency()).isEqualTo("USD");
        assertThat(created.getCreatedAt()).isEqualTo(NOW);
        assertThat(created.getUpdatedAt()).isEqualTo(NOW);
    }
    @Test void preservesExplicitCurrency() {
        when(portfolios.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        assertThat(service.create(7, 7, "Savings", "KRW").getBaseCurrency()).isEqualTo("KRW");
    }
    @Test void rejectsCreatingOnBehalfOfAnotherUser() {
        assertThatThrownBy(() -> service.create(8, 7, "Stolen", "USD")).isInstanceOf(ApiException.class);
        verifyNoInteractions(portfolios);
    }
    @Test void updatesOnlyAnOwnedManagedAggregate() {
        Portfolio portfolio = new Portfolio(7, "Before", "USD", NOW.minusSeconds(1));
        when(portfolios.findByIdAndUserId(2, 7)).thenReturn(Optional.of(portfolio));
        assertThat(service.update(2, 7, "After", "KRW")).isSameAs(portfolio);
        assertThat(portfolio.getName()).isEqualTo("After");
        assertThat(portfolio.getBaseCurrency()).isEqualTo("KRW");
        assertThat(portfolio.getCreatedAt()).isEqualTo(NOW.minusSeconds(1));
        assertThat(portfolio.getUpdatedAt()).isEqualTo(NOW);
        verify(portfolios, never()).save(any());
    }
    @Test void rejectsUpdatingMissingPortfolio() {
        assertThatThrownBy(() -> service.update(2, 7, "After", null)).isInstanceOf(ApiException.class);
    }
    @Test void deletionLocksBeforeDeletingTheAggregate() {
        Portfolio portfolio = new Portfolio(7, "Savings", "USD", NOW.minusSeconds(1));
        when(portfolios.lockOwned(2, 7)).thenReturn(Optional.of(portfolio));
        service.delete(2, 7);
        var order = inOrder(portfolios);
        order.verify(portfolios).lockOwned(2, 7);
        order.verify(portfolios).delete(portfolio);
    }
    @Test void deletionCannotAffectAnUnownedRow() {
        assertThatThrownBy(() -> service.delete(2, 7)).isInstanceOf(ApiException.class);
        verify(portfolios, never()).delete(any());
    }
}

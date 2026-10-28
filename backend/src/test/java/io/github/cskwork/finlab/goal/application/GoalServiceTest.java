package io.github.cskwork.finlab.goal.application;

import io.github.cskwork.finlab.common.error.ApiException;
import io.github.cskwork.finlab.goal.domain.Goal;
import io.github.cskwork.finlab.goal.persistence.GoalRepository;
import io.github.cskwork.finlab.portfolio.application.PortfolioService;
import java.math.BigDecimal;
import java.time.Instant;
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
class GoalServiceTest {
    @Mock GoalRepository goals;
    @Mock PortfolioService portfolios;
    GoalService service;
    static final Instant TARGET = Instant.parse("2030-01-01T00:00:00Z");
    @BeforeEach void setUp() { service = new GoalService(goals, portfolios); }
    @Test void verifiesOwnershipBeforeListing() {
        Goal goal = new Goal(2, "Home", BigDecimal.TEN, TARGET, BigDecimal.ONE);
        when(goals.findByPortfolioIdOrderByIdAsc(2)).thenReturn(List.of(goal));
        assertThat(service.list(2, 7)).containsExactly(goal);
        var order = inOrder(portfolios, goals);
        order.verify(portfolios).requireOwned(2, 7);
        order.verify(goals).findByPortfolioIdOrderByIdAsc(2);
    }
    @Test void doesNotListAnotherUsersGoals() {
        doThrow(ApiException.notFound()).when(portfolios).requireOwned(2, 7);
        assertThatThrownBy(() -> service.list(2, 7)).isInstanceOf(ApiException.class);
        verifyNoInteractions(goals);
    }
    @Test void createsGoalWithDefaultProgress() {
        when(goals.save(any())).thenAnswer(i -> i.getArgument(0));
        Goal goal = service.create(2, 7, command(2, null));
        assertThat(goal.getPortfolioId()).isEqualTo(2);
        assertThat(goal.getCurrentAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(portfolios).lockOwned(2, 7);
    }
    @Test void retainsProvidedProgress() {
        when(goals.save(any())).thenAnswer(i -> i.getArgument(0));
        Goal goal = service.create(2, 7, command(2, BigDecimal.ONE));
        assertThat(goal.getName()).isEqualTo("Home");
        assertThat(goal.getTargetAmount()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(goal.getTargetDate()).isEqualTo(TARGET);
        assertThat(goal.getCurrentAmount()).isEqualByComparingTo(BigDecimal.ONE);
    }
    @Test void rejectsConflictingPathAndBodyIds() {
        assertThatThrownBy(() -> service.create(2, 7, command(3, null)))
                .isInstanceOfSatisfying(ApiException.class, exception -> assertThat(exception.status()).isEqualTo(400));
        verifyNoInteractions(portfolios, goals);
    }
    @Test void creationChecksTheOwnerBeforePersisting() {
        doThrow(ApiException.notFound()).when(portfolios).lockOwned(2, 7);
        assertThatThrownBy(() -> service.create(2, 7, command(2, null))).isInstanceOf(ApiException.class);
        verifyNoInteractions(goals);
    }
    @Test void ownedGoalCanBeDeleted() {
        Goal goal = new Goal(2, "Home", BigDecimal.TEN, TARGET, BigDecimal.ZERO);
        when(goals.findById(5L)).thenReturn(Optional.of(goal));
        service.delete(5, 7);
        var order = inOrder(portfolios, goals);
        order.verify(goals).findById(5L);
        order.verify(portfolios).lockOwned(2, 7);
        order.verify(goals).delete(goal);
    }
    @Test void missingGoalReturnsNotFound() {
        assertThatThrownBy(() -> service.delete(5, 7)).isInstanceOf(ApiException.class);
        verifyNoInteractions(portfolios);
        verify(goals, never()).delete(any());
    }
    @Test void unownedGoalCannotBeDeleted() {
        Goal goal = new Goal(2, "Home", BigDecimal.TEN, TARGET, BigDecimal.ZERO);
        when(goals.findById(5L)).thenReturn(Optional.of(goal));
        doThrow(ApiException.notFound()).when(portfolios).lockOwned(2, 7);
        assertThatThrownBy(() -> service.delete(5, 7)).isInstanceOf(ApiException.class);
        verify(goals, never()).delete(any());
    }
    private GoalService.CreateGoal command(long portfolioId, BigDecimal progress) {
        return new GoalService.CreateGoal(portfolioId, "Home", BigDecimal.TEN, TARGET, progress);
    }
}

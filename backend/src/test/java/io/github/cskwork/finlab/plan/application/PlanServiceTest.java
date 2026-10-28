package io.github.cskwork.finlab.plan.application;

import io.github.cskwork.finlab.common.error.ApiException;
import io.github.cskwork.finlab.plan.domain.Plan;
import io.github.cskwork.finlab.plan.domain.PlanInterval;
import io.github.cskwork.finlab.plan.persistence.PlanRepository;
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
class PlanServiceTest {
    @Mock PlanRepository plans;
    @Mock PortfolioService portfolios;
    PlanService service;
    static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    @BeforeEach void setUp() { service = new PlanService(plans, portfolios); }
    @Test void verifiesOwnershipBeforeListingPlans() {
        Plan plan = plan();
        when(plans.findByPortfolioIdOrderByIdAsc(2)).thenReturn(List.of(plan));
        assertThat(service.list(2, 7)).containsExactly(plan);
        var order = inOrder(portfolios, plans);
        order.verify(portfolios).requireOwned(2, 7);
        order.verify(plans).findByPortfolioIdOrderByIdAsc(2);
    }
    @Test void unownedPortfolioCannotBeListed() {
        doThrow(ApiException.notFound()).when(portfolios).requireOwned(2, 7);
        assertThatThrownBy(() -> service.list(2, 7)).isInstanceOf(ApiException.class);
        verifyNoInteractions(plans);
    }
    @Test void defaultsToActiveAndPreservesInput() {
        when(plans.save(any())).thenAnswer(i -> i.getArgument(0));
        Plan created = service.create(2, 7, command(2, null));
        assertThat(created.getPortfolioId()).isEqualTo(2);
        assertThat(created.getSymbol()).isEqualTo("AAPL");
        assertThat(created.getAmountPerInterval()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(created.getInterval()).isEqualTo(PlanInterval.MONTHLY);
        assertThat(created.getStartDate()).isEqualTo(START);
        assertThat(created.getEndDate()).isNull();
        assertThat(created.isActive()).isTrue();
        verify(portfolios).lockOwned(2, 7);
    }
    @Test void preservesExplicitActive() {
        when(plans.save(any())).thenAnswer(i -> i.getArgument(0));
        assertThat(service.create(2, 7, command(2, true)).isActive()).isTrue();
    }
    @Test void preservesExplicitInactive() {
        when(plans.save(any())).thenAnswer(i -> i.getArgument(0));
        assertThat(service.create(2, 7, command(2, false)).isActive()).isFalse();
    }
    @Test void refusesConflictingPortfolioIds() {
        assertThatThrownBy(() -> service.create(2, 7, command(3, true)))
                .isInstanceOfSatisfying(ApiException.class, exception -> assertThat(exception.status()).isEqualTo(400));
        verifyNoInteractions(portfolios, plans);
    }
    @Test void checksOwnershipBeforeSaving() {
        doThrow(ApiException.notFound()).when(portfolios).lockOwned(2, 7);
        assertThatThrownBy(() -> service.create(2, 7, command(2, null))).isInstanceOf(ApiException.class);
        verifyNoInteractions(plans);
    }
    @Test void deletesOnlyAnOwnedPlan() {
        Plan plan = plan();
        when(plans.findById(5L)).thenReturn(Optional.of(plan));
        service.delete(5, 7);
        var order = inOrder(portfolios, plans);
        order.verify(plans).findById(5L);
        order.verify(portfolios).lockOwned(2, 7);
        order.verify(plans).delete(plan);
    }
    @Test void missingPlanUsesNotFound() {
        assertThatThrownBy(() -> service.delete(5, 7)).isInstanceOf(ApiException.class);
        verifyNoInteractions(portfolios);
        verify(plans, never()).delete(any());
    }
    @Test void unownedPlanCannotBeDeleted() {
        when(plans.findById(5L)).thenReturn(Optional.of(plan()));
        doThrow(ApiException.notFound()).when(portfolios).lockOwned(2, 7);
        assertThatThrownBy(() -> service.delete(5, 7)).isInstanceOf(ApiException.class);
        verify(plans, never()).delete(any());
    }
    private PlanService.CreatePlan command(long id, Boolean active) {
        return new PlanService.CreatePlan(id, "AAPL", BigDecimal.TEN, PlanInterval.MONTHLY, START, null, active);
    }
    private Plan plan() { return new Plan(2, "AAPL", BigDecimal.TEN, PlanInterval.MONTHLY, START, null, true); }
}

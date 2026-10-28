package io.github.cskwork.finlab.plan.application;

import io.github.cskwork.finlab.common.error.ApiException;
import io.github.cskwork.finlab.plan.domain.Plan;
import io.github.cskwork.finlab.plan.domain.PlanInterval;
import io.github.cskwork.finlab.plan.persistence.PlanRepository;
import io.github.cskwork.finlab.portfolio.application.PortfolioService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PlanService {
    private final PlanRepository plans;
    private final PortfolioService portfolios;
    public PlanService(PlanRepository plans, PortfolioService portfolios) {
        this.plans = plans;
        this.portfolios = portfolios;
    }
    public List<Plan> list(long portfolioId, long userId) {
        portfolios.requireOwned(portfolioId, userId);
        return plans.findByPortfolioIdOrderByIdAsc(portfolioId);
    }
    @Transactional
    public Plan create(long pathPortfolioId, long userId, CreatePlan command) {
        if (pathPortfolioId != command.portfolioId()) {
            throw new ApiException(400, "VALIDATION_ERROR", "portfolioId must match the path");
        }
        portfolios.lockOwned(pathPortfolioId, userId);
        return plans.save(new Plan(pathPortfolioId, command.symbol(), command.amountPerInterval(), command.interval(),
                command.startDate(), command.endDate(), command.isActive() == null || command.isActive()));
    }
    @Transactional
    public void delete(long id, long userId) {
        Plan plan = plans.findById(id).orElseThrow(ApiException::notFound);
        portfolios.lockOwned(plan.getPortfolioId(), userId);
        plans.delete(plan);
    }
    public record CreatePlan(long portfolioId, String symbol, BigDecimal amountPerInterval, PlanInterval interval,
                             Instant startDate, Instant endDate, Boolean isActive) {}
}

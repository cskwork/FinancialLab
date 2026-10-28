package io.github.cskwork.finlab.goal.application;

import io.github.cskwork.finlab.common.error.ApiException;
import io.github.cskwork.finlab.goal.domain.Goal;
import io.github.cskwork.finlab.goal.persistence.GoalRepository;
import io.github.cskwork.finlab.portfolio.application.PortfolioService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GoalService {
    private final GoalRepository goals;
    private final PortfolioService portfolios;
    public GoalService(GoalRepository goals, PortfolioService portfolios) {
        this.goals = goals;
        this.portfolios = portfolios;
    }
    public List<Goal> list(long portfolioId, long userId) {
        portfolios.requireOwned(portfolioId, userId);
        return goals.findByPortfolioIdOrderByIdAsc(portfolioId);
    }
    @Transactional
    public Goal create(long pathPortfolioId, long userId, CreateGoal command) {
        if (pathPortfolioId != command.portfolioId()) {
            throw new ApiException(400, "VALIDATION_ERROR", "portfolioId must match the path");
        }
        portfolios.lockOwned(pathPortfolioId, userId);
        return goals.save(new Goal(pathPortfolioId, command.name(), command.targetAmount(), command.targetDate(),
                command.currentAmount() == null ? BigDecimal.ZERO : command.currentAmount()));
    }
    @Transactional
    public void delete(long id, long userId) {
        Goal goal = goals.findById(id).orElseThrow(ApiException::notFound);
        portfolios.lockOwned(goal.getPortfolioId(), userId);
        goals.delete(goal);
    }
    public record CreateGoal(long portfolioId, String name, BigDecimal targetAmount, Instant targetDate, BigDecimal currentAmount) {}
}

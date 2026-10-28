package io.github.cskwork.finlab.goal.persistence;

import io.github.cskwork.finlab.goal.domain.Goal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalRepository extends JpaRepository<Goal, Long> {
    List<Goal> findByPortfolioIdOrderByIdAsc(long portfolioId);
}

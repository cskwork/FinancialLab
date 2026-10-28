package io.github.cskwork.finlab.plan.persistence;

import io.github.cskwork.finlab.plan.domain.Plan;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRepository extends JpaRepository<Plan, Long> {
    List<Plan> findByPortfolioIdOrderByIdAsc(long portfolioId);
}

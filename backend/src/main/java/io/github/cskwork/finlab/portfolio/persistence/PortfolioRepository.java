package io.github.cskwork.finlab.portfolio.persistence;

import io.github.cskwork.finlab.portfolio.domain.Portfolio;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioRepository extends JpaRepository<Portfolio, Long>, PortfolioLockingRepository {
    List<Portfolio> findByUserIdOrderByIdAsc(long userId);
    Optional<Portfolio> findByIdAndUserId(long id, long userId);

}

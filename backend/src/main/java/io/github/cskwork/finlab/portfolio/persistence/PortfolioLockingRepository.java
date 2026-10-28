package io.github.cskwork.finlab.portfolio.persistence;

import io.github.cskwork.finlab.portfolio.domain.Portfolio;
import java.util.Optional;

public interface PortfolioLockingRepository {
    Optional<Portfolio> lockOwned(long id, long userId);
}

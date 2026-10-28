package io.github.cskwork.finlab.portfolio.application;

import io.github.cskwork.finlab.common.error.ApiException;
import io.github.cskwork.finlab.portfolio.domain.Portfolio;
import io.github.cskwork.finlab.portfolio.persistence.PortfolioRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PortfolioService {
    private final PortfolioRepository portfolios;
    private final Clock clock;

    public PortfolioService(PortfolioRepository portfolios, Clock clock) {
        this.portfolios = portfolios;
        this.clock = clock;
    }

    public List<Portfolio> list(long requestedUserId, long userId) {
        requireSameUser(requestedUserId, userId);
        return portfolios.findByUserIdOrderByIdAsc(userId);
    }

    public Portfolio requireOwned(long id, long userId) {
        return portfolios.findByIdAndUserId(id, userId).orElseThrow(ApiException::notFound);
    }

    @Transactional
    public Portfolio lockOwned(long id, long userId) {
        return portfolios.lockOwned(id, userId).orElseThrow(ApiException::notFound);
    }

    @Transactional
    public Portfolio create(long requestedUserId, long userId, String name, String baseCurrency) {
        requireSameUser(requestedUserId, userId);
        return portfolios.save(new Portfolio(userId, name, baseCurrency == null ? "USD" : baseCurrency, clock.instant()));
    }

    @Transactional
    public Portfolio update(long id, long userId, String name, String baseCurrency) {
        Portfolio portfolio = requireOwned(id, userId);
        portfolio.update(name, baseCurrency, clock.instant());
        return portfolio;
    }

    @Transactional
    public void delete(long id, long userId) { portfolios.delete(lockOwned(id, userId)); }

    private void requireSameUser(long requestedUserId, long userId) {
        if (requestedUserId != userId) throw ApiException.notFound();
    }
}

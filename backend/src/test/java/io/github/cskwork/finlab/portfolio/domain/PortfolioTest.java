package io.github.cskwork.finlab.portfolio.domain;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;

class PortfolioTest {
    private static final Instant CREATED = Instant.parse("2026-10-28T00:00:00Z");
    private static final Instant UPDATED = CREATED.plusSeconds(30);

    @Test void initializesOwnerCurrencyAndSuppliedUtcTimestamps() {
        Portfolio portfolio = new Portfolio(7, "Savings", "USD", CREATED);
        ReflectionTestUtils.setField(portfolio, "id", 42L);
        assertThat(portfolio.getId()).isEqualTo(42);
        assertThat(portfolio.getUserId()).isEqualTo(7);
        assertThat(portfolio.getName()).isEqualTo("Savings");
        assertThat(portfolio.getBaseCurrency()).isEqualTo("USD");
        assertThat(portfolio.getCreatedAt()).isEqualTo(CREATED);
        assertThat(portfolio.getUpdatedAt()).isEqualTo(CREATED);
    }

    @Test void patchKeepsOmittedFieldsAndUsesTheSuppliedUpdateTime() {
        Portfolio portfolio = new Portfolio(7, "Savings", "USD", CREATED);
        portfolio.update(null, null, UPDATED);
        assertThat(portfolio.getName()).isEqualTo("Savings");
        assertThat(portfolio.getBaseCurrency()).isEqualTo("USD");
        assertThat(portfolio.getCreatedAt()).isEqualTo(CREATED);
        assertThat(portfolio.getUpdatedAt()).isEqualTo(UPDATED);
        portfolio.update("New", null, UPDATED.plusSeconds(1));
        assertThat(portfolio.getName()).isEqualTo("New");
        assertThat(portfolio.getBaseCurrency()).isEqualTo("USD");
        portfolio.update(null, "KRW", UPDATED.plusSeconds(2));
        assertThat(portfolio.getName()).isEqualTo("New");
        assertThat(portfolio.getBaseCurrency()).isEqualTo("KRW");
        assertThat(portfolio.getCreatedAt()).isEqualTo(CREATED);
        assertThat(portfolio.getUpdatedAt()).isEqualTo(UPDATED.plusSeconds(2));
    }
}

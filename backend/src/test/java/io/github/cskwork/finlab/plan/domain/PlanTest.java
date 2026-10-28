package io.github.cskwork.finlab.plan.domain;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;

class PlanTest {
    @Test void retainsDatesFrequencyAndDecimalAmount() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end = Instant.parse("2030-01-01T00:00:00Z");
        Plan plan = new Plan(2, "AAPL", new BigDecimal("123.4567"), PlanInterval.WEEKLY, start, end, false);
        ReflectionTestUtils.setField(plan, "id", 5L);
        assertThat(plan.getId()).isEqualTo(5);
        assertThat(plan.getPortfolioId()).isEqualTo(2);
        assertThat(plan.getSymbol()).isEqualTo("AAPL");
        assertThat(plan.getAmountPerInterval()).isEqualByComparingTo("123.4567");
        assertThat(plan.getInterval()).isEqualTo(PlanInterval.WEEKLY);
        assertThat(plan.getStartDate()).isEqualTo(start);
        assertThat(plan.getEndDate()).isEqualTo(end);
        assertThat(plan.isActive()).isFalse();
    }
}

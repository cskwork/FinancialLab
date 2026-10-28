package io.github.cskwork.finlab.goal.domain;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;

class GoalTest {
    @Test void retainsTheGoalValuesWithoutFloatingPointConversion() {
        Instant target = Instant.parse("2030-01-01T00:00:00Z");
        Goal goal = new Goal(2, "Home", new BigDecimal("10000.1234"), target, new BigDecimal("321.1234"));
        ReflectionTestUtils.setField(goal, "id", 5L);
        assertThat(goal.getId()).isEqualTo(5);
        assertThat(goal.getPortfolioId()).isEqualTo(2);
        assertThat(goal.getName()).isEqualTo("Home");
        assertThat(goal.getTargetAmount()).isEqualByComparingTo("10000.1234");
        assertThat(goal.getTargetDate()).isEqualTo(target);
        assertThat(goal.getCurrentAmount()).isEqualByComparingTo("321.1234");
    }
}

package io.github.cskwork.finlab.config;

import jakarta.validation.Validation;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FinLabPropertiesTest {
    @Test void defaultsAreValidAndOperationalLimitsAreBounded() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var defaults = FinLabProperties.defaults();
            assertThat(validator.validate(defaults)).isEmpty();
            assertThat(defaults.ledger().lockTimeoutMs()).isEqualTo(3000);
            assertThat(defaults.login().hashingPermits()).isEqualTo(2);
            assertThat(defaults.login().hashingWait()).isEqualTo(Duration.ofMillis(500));
            assertThat(new FinLabProperties.Login(1, 1, 1, null, 2, Duration.ofSeconds(6)).isHashingWaitValid()).isFalse();
            var invalid = new FinLabProperties(new FinLabProperties.Cors(List.of("*")),
                    new FinLabProperties.Idempotency(Duration.ZERO, Duration.ofSeconds(-1)),
                    new FinLabProperties.Login(0, 0, 0, Duration.ZERO, 1, Duration.ZERO), new FinLabProperties.Ledger(0, 10001));
            assertThat(validator.validate(invalid)).hasSizeGreaterThanOrEqualTo(8);
        }
    }
}

package io.github.cskwork.finlab.auth.config;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class JwtPropertiesTest {
    @Test void providesSafeLocalDefaultsAndAcceptsOverrides() {
        var defaults = new JwtProperties(null, null, null, null, null);
        assertThat(defaults.issuer()).isEqualTo("finlab");
        assertThat(defaults.audience()).isEqualTo("finlab-api");
        assertThat(defaults.ttl()).isEqualTo(Duration.ofMinutes(15));
        var overrides = new JwtProperties("issuer", "audience", Duration.ofMinutes(5), "private", "public");
        assertThat(overrides.privateKeyLocation()).isEqualTo("private");
        assertThat(overrides.publicKeyLocation()).isEqualTo("public");
    }
    @Test void rejectsEmptyIdentityAndUnboundedLifetimes() {
        assertThatThrownBy(() -> new JwtProperties("", "aud", null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtProperties("issuer", " ", null, null, null)).isInstanceOf(IllegalArgumentException.class);
        for (var ttl : new Duration[]{Duration.ZERO, Duration.ofSeconds(-1), Duration.ofHours(2)}) {
            assertThatThrownBy(() -> new JwtProperties("issuer", "aud", ttl, null, null)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}

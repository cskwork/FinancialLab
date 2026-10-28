package io.github.cskwork.finlab.auth.domain;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class UserTest {
    @Test void keepsPasswordHashInDomainAndHasNoGeneratedIdBeforePersistence() {
        var user = new User("demo@finlab.app", "{bcrypt}hash", "USER");
        assertThat(user.getId()).isNull();
        assertThat(user.getEmail()).isEqualTo("demo@finlab.app");
        assertThat(user.getPasswordHash()).isEqualTo("{bcrypt}hash");
        assertThat(user.getRole()).isEqualTo("USER");
    }
}

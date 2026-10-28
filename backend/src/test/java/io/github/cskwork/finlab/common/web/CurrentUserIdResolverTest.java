package io.github.cskwork.finlab.common.web;

import io.github.cskwork.finlab.common.error.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import static org.assertj.core.api.Assertions.*;

class CurrentUserIdResolverTest {
    private final CurrentUserIdResolver resolver = new CurrentUserIdResolver();
    void example(@CurrentUserId long userId, String other) { }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void resolvesOnlyAnnotatedNumericParametersFromValidatedBearerIdentity() throws Exception {
        var method = getClass().getDeclaredMethod("example", long.class, String.class);
        assertThat(resolver.supportsParameter(new MethodParameter(method, 0))).isTrue();
        assertThat(resolver.supportsParameter(new MethodParameter(method, 1))).isFalse();
        authenticate("42");
        assertThat(resolver.resolveArgument(null, null, null, null)).isEqualTo(42L);
    }
    @Test void missingOrMalformedIdentityIsAnAuthenticationProblem() {
        assertThatThrownBy(() -> resolver.resolveArgument(null, null, null, null)).isInstanceOf(ApiException.class);
        authenticate("invalid");
        assertThatThrownBy(() -> resolver.resolveArgument(null, null, null, null))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.status()).isEqualTo(401));
    }
    private void authenticate(String subject) {
        var jwt = Jwt.withTokenValue("test").header("alg", "RS256").subject(subject).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}

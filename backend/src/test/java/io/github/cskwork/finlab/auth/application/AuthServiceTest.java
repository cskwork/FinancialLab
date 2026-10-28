package io.github.cskwork.finlab.auth.application;

import io.github.cskwork.finlab.auth.domain.User;
import io.github.cskwork.finlab.auth.config.JwtProperties;
import io.github.cskwork.finlab.config.FinLabProperties;
import io.github.cskwork.finlab.auth.persistence.UserRepository;
import io.github.cskwork.finlab.common.error.ApiException;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final JwtEncoder tokens = mock(JwtEncoder.class);
    private final Instant now = Instant.parse("2026-10-28T00:00:00Z");
    private AuthService service;

    @BeforeEach void setUp() {
        when(passwords.encode(anyString())).thenReturn("dummy-hash");
        var clock = Clock.fixed(now, ZoneOffset.UTC);
        var settings = FinLabProperties.defaults();
        service = new AuthService(users, passwords, tokens, new JwtProperties(null, null, null, null, null), clock,
            settings, new LoginAttemptLimiter(settings, clock));
    }

    @Test void issuesShortLivedRs256TokenWithIdentityAndAudience() {
        var user = mock(User.class);
        when(user.getId()).thenReturn(7L);
        when(user.getEmail()).thenReturn("demo@finlab.app");
        when(user.getPasswordHash()).thenReturn("stored-hash");
        when(user.getRole()).thenReturn("USER");
        when(users.findByEmailIgnoreCase("demo@finlab.app")).thenReturn(Optional.of(user));
        when(passwords.matches("supplied", "stored-hash")).thenReturn(true);
        when(tokens.encode(any())).thenReturn(Jwt.withTokenValue("access-token").header("alg", "RS256").subject("7").build());
        var result = service.login(" demo@finlab.app ", "supplied", "127.0.0.1");
        assertThat(result.userId()).isEqualTo(7);
        assertThat(result.email()).isEqualTo("demo@finlab.app");
        assertThat(result.role()).isEqualTo("USER");
        assertThat(result.token()).isEqualTo("access-token");
        var parameters = ArgumentCaptor.forClass(JwtEncoderParameters.class);
        verify(tokens).encode(parameters.capture());
        var claims = parameters.getValue().getClaims();
        assertThat(claims.getClaimAsString("iss")).isEqualTo("finlab");
        assertThat(claims.getAudience()).containsExactly("finlab-api");
        assertThat(claims.getSubject()).isEqualTo("7");
        assertThat(claims.getIssuedAt()).isEqualTo(now);
        assertThat(claims.getExpiresAt()).isEqualTo(now.plusSeconds(900));
        assertThat(claims.getClaimAsString("role")).isEqualTo("USER");
        assertThat(parameters.getValue().getJwsHeader().getAlgorithm().getName()).isEqualTo("RS256");
        assertThat(claims.getClaims()).doesNotContainKeys("password", "passwordHash");
    }

    @Test void unknownAccountStillChecksDummyPasswordAndReturnsGeneric401() {
        when(users.findByEmailIgnoreCase("unknown@example.com")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.login("unknown@example.com", "secret", "127.0.0.1"))
            .isInstanceOfSatisfying(ApiException.class, error -> {
                assertThat(error.status()).isEqualTo(401);
                assertThat(error.code()).isEqualTo("INVALID_CREDENTIALS");
            });
        verify(passwords).matches("secret", "dummy-hash");
        verifyNoInteractions(tokens);
    }

    @Test void wrongPasswordAndMalformedHashDoNotIssueTokens() {
        var user = new User("demo@finlab.app", "invalid-hash", "USER");
        when(users.findByEmailIgnoreCase("demo@finlab.app")).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> service.login("demo@finlab.app", "wrong", "127.0.0.1")).isInstanceOf(ApiException.class);
        when(passwords.matches("wrong", "invalid-hash")).thenThrow(new IllegalArgumentException("bad hash"));
        assertThatThrownBy(() -> service.login("demo@finlab.app", "wrong", "127.0.0.1"))
            .isInstanceOf(ApiException.class).hasMessage("이메일 또는 비밀번호가 올바르지 않습니다");
        verifyNoInteractions(tokens);
    }

    @Test void unknownAccountCannotAuthenticateEvenIfDummyHashMatches() {
        when(users.findByEmailIgnoreCase("unknown@example.com")).thenReturn(Optional.empty());
        when(passwords.matches(anyString(), eq("dummy-hash"))).thenReturn(true);
        assertThatThrownBy(() -> service.login("unknown@example.com", "anything", "127.0.0.1")).isInstanceOf(ApiException.class);
        verifyNoInteractions(tokens);
    }
}

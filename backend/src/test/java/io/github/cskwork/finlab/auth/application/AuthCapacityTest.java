package io.github.cskwork.finlab.auth.application;

import io.github.cskwork.finlab.auth.config.JwtProperties;
import io.github.cskwork.finlab.auth.persistence.UserRepository;
import io.github.cskwork.finlab.common.error.ApiException;
import io.github.cskwork.finlab.config.FinLabProperties;
import java.time.*;
import java.util.Optional;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthCapacityTest {
    @Test void waitsForConfiguredTimeoutBeforeRejectingExcessWorkAndReleasesPermitAfterFailure() throws Exception {
        var users = mock(UserRepository.class);
        var passwords = mock(PasswordEncoder.class);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(users.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(passwords.encode(anyString())).thenReturn("dummy-hash");
        when(passwords.matches(anyString(), anyString())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            entered.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return false;
        });
        var clock = Clock.systemUTC();
        var settings = new FinLabProperties(null, null, new FinLabProperties.Login(1, 100, 100, Duration.ofMinutes(1), 100, Duration.ofMillis(60)), null);
        var service = new AuthService(users, passwords, mock(JwtEncoder.class), new JwtProperties(null, null, null, null, null),
            clock, settings, new LoginAttemptLimiter(settings, clock));
        try (var workers = Executors.newSingleThreadExecutor()) {
            var first = workers.submit(() -> {
                assertThatThrownBy(() -> service.login("first@example.com", "temporary", "192.0.2.1"))
                    .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.status()).isEqualTo(401));
            });
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                long started = System.nanoTime();
                assertThatThrownBy(() -> service.login("second@example.com", "temporary", "192.0.2.2"))
                    .isInstanceOfSatisfying(ApiException.class, error -> {
                        assertThat(error.status()).isEqualTo(503);
                        assertThat(error.code()).isEqualTo("AUTHENTICATION_BUSY");
                        assertThat(error.retryAfterSeconds()).isEqualTo(1);
                    });
                assertThat(Duration.ofNanos(System.nanoTime() - started)).isGreaterThanOrEqualTo(Duration.ofMillis(60));
                verify(users, never()).findByEmailIgnoreCase("second@example.com");
            } finally { release.countDown(); }
            first.get(5, TimeUnit.SECONDS);
            assertThatThrownBy(() -> service.login("third@example.com", "temporary", "192.0.2.3"))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.status()).isEqualTo(401));
        }
    }

    @Test void usesCapacityReleasedDuringTheWaitInsteadOfReturning503() throws Exception {
        var users = mock(UserRepository.class);
        var passwords = mock(PasswordEncoder.class);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(users.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(passwords.encode(anyString())).thenReturn("dummy-hash");
        when(passwords.matches(eq("first"), anyString())).thenAnswer(invocation -> {
            entered.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return false;
        });
        var clock = Clock.systemUTC();
        var settings = new FinLabProperties(null, null,
            new FinLabProperties.Login(1, 100, 100, Duration.ofMinutes(1), 100, Duration.ofSeconds(2)), null);
        var service = new AuthService(users, passwords, mock(JwtEncoder.class), new JwtProperties(null, null, null, null, null),
            clock, settings, new LoginAttemptLimiter(settings, clock));
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> loginFailure(service, "first"));
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                var waiting = workers.submit(() -> loginFailure(service, "second"));
                assertThatThrownBy(() -> waiting.get(100, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                release.countDown();
                assertThat(waiting.get(5, TimeUnit.SECONDS).status()).isEqualTo(401);
                verify(users).findByEmailIgnoreCase("second@example.com");
            } finally { release.countDown(); }
            assertThat(first.get(5, TimeUnit.SECONDS).status()).isEqualTo(401);
        }
    }

    @Test void interruptedWaitReturns503AndPreservesInterruptWithoutLeakingAPermit() throws Exception {
        var users = mock(UserRepository.class);
        var passwords = mock(PasswordEncoder.class);
        when(passwords.encode(anyString())).thenReturn("dummy-hash");
        var clock = Clock.systemUTC();
        var settings = FinLabProperties.defaults();
        var service = new AuthService(users, passwords, mock(JwtEncoder.class), new JwtProperties(null, null, null, null, null),
            clock, settings, new LoginAttemptLimiter(settings, clock));
        var thread = new Thread(() -> {
            Thread.currentThread().interrupt();
            var failure = loginFailure(service, "interrupted");
            assertThat(failure.status()).isEqualTo(503);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        });
        var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        thread.setUncaughtExceptionHandler((ignored, error) -> failure.set(error));
        thread.start();
        thread.join(5000);
        assertThat(thread.isAlive()).isFalse();
        assertThat(failure.get()).isNull();
        verifyNoInteractions(users);
        assertThat(loginFailure(service, "next").status()).isEqualTo(401);
    }

    private static ApiException loginFailure(AuthService service, String user) {
        return catchThrowableOfType(ApiException.class,
            () -> service.login(user + "@example.com", user, "192.0.2.1"));
    }

    @Test void repositoryFailureAlsoReleasesHashingPermitAndRepositoryOwnsReadOnlyBoundary() throws Exception {
        var users = mock(UserRepository.class);
        var passwords = mock(PasswordEncoder.class);
        when(passwords.encode(anyString())).thenReturn("dummy-hash");
        when(users.findByEmailIgnoreCase(anyString())).thenThrow(new IllegalStateException("repository failure"))
            .thenReturn(Optional.empty());
        var clock = Clock.systemUTC();
        var settings = new FinLabProperties(null, null, new FinLabProperties.Login(1, 100, 100, Duration.ofMinutes(1), 100, null), null);
        var service = new AuthService(users, passwords, mock(JwtEncoder.class), new JwtProperties(null, null, null, null, null),
            clock, settings, new LoginAttemptLimiter(settings, clock));
        assertThatThrownBy(() -> service.login("first@example.com", "temporary", "192.0.2.1"))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.login("first@example.com", "temporary", "192.0.2.1"))
            .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.status()).isEqualTo(401));
        assertThat(AuthService.class.getAnnotation(Transactional.class)).isNull();
        assertThat(AuthService.class.getMethod("login", String.class, String.class, String.class).getAnnotation(Transactional.class)).isNull();
        assertThat(UserRepository.class.getMethod("findByEmailIgnoreCase", String.class).getAnnotation(Transactional.class).readOnly()).isTrue();
    }
}

package io.github.cskwork.finlab.auth.application;

import io.github.cskwork.finlab.auth.persistence.UserRepository;
import io.github.cskwork.finlab.auth.config.JwtProperties;
import io.github.cskwork.finlab.config.FinLabProperties;
import io.github.cskwork.finlab.auth.domain.User;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.time.Duration;
import io.github.cskwork.finlab.common.error.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Clock clock;
    private final String dummyHash;
    private final Semaphore hashingSlots;
    private final Duration hashingWait;
    private final LoginAttemptLimiter attempts;

    public AuthService(UserRepository users, PasswordEncoder passwords, JwtEncoder encoder,
                       JwtProperties properties, Clock clock, FinLabProperties applicationProperties, LoginAttemptLimiter attempts) {
        this.users = users;
        this.passwords = passwords;
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
        this.hashingSlots = new Semaphore(applicationProperties.login().hashingPermits());
        this.hashingWait = applicationProperties.login().hashingWait();
        this.attempts = attempts;
        // Unknown accounts still run a real password check to reduce account enumeration by timing.
        this.dummyHash = passwords.encode("unusable-dummy-account-password");
    }

    public LoginResult login(String email, String password, String clientAddress) {
        String normalized = LoginAttemptLimiter.normalizeEmail(email);
        attempts.check(clientAddress, normalized);
        if (!acquireHashingSlot()) {
            throw new ApiException(503, "AUTHENTICATION_BUSY", "로그인 요청을 처리 중입니다. 잠시 후 다시 시도하세요", 1);
        }
        Optional<User> user;
        boolean matches;
        try {
            // The repository's short read-only transaction ends before CPU-bound password hashing.
            user = users.findByEmailIgnoreCase(normalized);
            var hash = user.map(User::getPasswordHash).orElse(dummyHash);
            try { matches = passwords.matches(password, hash); }
            catch (IllegalArgumentException invalidStoredHash) { matches = false; }
        } finally {
            hashingSlots.release();
        }
        if (!matches || user.isEmpty()) {
            throw new ApiException(401, "INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다");
        }
        var account = user.orElseThrow();
        Instant now = clock.instant();
        var claims = JwtClaimsSet.builder().issuer(properties.issuer())
            .audience(List.of(properties.audience())).subject(account.getId().toString())
            .issuedAt(now).expiresAt(now.plus(properties.ttl())).claim("role", account.getRole()).build();
        var header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new LoginResult(account.getId(), account.getEmail(), account.getRole(), token);
    }

    private boolean acquireHashingSlot() {
        try {
            return hashingSlots.tryAcquire(hashingWait.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public record LoginResult(long userId, String email, String role, String token) {}
}

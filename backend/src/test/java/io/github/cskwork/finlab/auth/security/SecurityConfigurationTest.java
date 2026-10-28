package io.github.cskwork.finlab.auth.security;

import io.github.cskwork.finlab.auth.config.JwtProperties;
import io.github.cskwork.finlab.config.FinLabProperties;
import org.springframework.mock.web.MockHttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import static org.assertj.core.api.Assertions.*;

class SecurityConfigurationTest {
    private final SecurityConfiguration configuration = new SecurityConfiguration();
    private final JwtProperties properties = new JwtProperties(null, null, null, null, null);

    @Test void corsExposesTransactionCountWithoutAllowingWildcardOrigins() {
        var cors = configuration.corsConfigurationSource(FinLabProperties.defaults())
                .getCorsConfiguration(new MockHttpServletRequest("GET", "/v1/portfolios/1/transactions"));
        assertThat(cors.getExposedHeaders()).contains("X-Total-Count", "X-Request-Id", "Retry-After");
        assertThat(cors.getAllowedOrigins()).containsExactly("http://localhost:5173");
    }

    @Test void productionRequiresKeysAndPartialKeyConfigurationAlwaysFailsClosed() {
        assertThatThrownBy(() -> configuration.signingKey(properties, new MockEnvironment(), new DefaultResourceLoader()))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("FINLAB_JWT_PRIVATE_KEY_LOCATION");
        var partial = new JwtProperties(null, null, null, "file:private.pem", null);
        assertThatThrownBy(() -> configuration.signingKey(partial, new MockEnvironment().withProperty("spring.profiles.active", "local"), new DefaultResourceLoader()))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("both");
    }

    @Test void validatesSignatureIssuerAudienceExpiryAndRequiredExpiration() throws Exception {
        var environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        var key = configuration.signingKey(properties, environment, new DefaultResourceLoader());
        var encoder = configuration.jwtEncoder(key);
        var decoder = configuration.jwtDecoder(key, properties);
        Instant now = Instant.now();
        assertThat(decoder.decode(token(encoder, "finlab", "finlab-api", "1", now.plusSeconds(900))).getSubject()).isEqualTo("1");
        for (String invalid : List.of(
                token(encoder, "wrong", "finlab-api", "1", now.plusSeconds(900)),
                token(encoder, "finlab", "wrong", "1", now.plusSeconds(900)),
                token(encoder, "finlab", "finlab-api", "not-a-number", now.plusSeconds(900)),
                token(encoder, "finlab", "finlab-api", "1", now.minusSeconds(300)),
                token(encoder, "finlab", "finlab-api", "1", null))) {
            assertThatThrownBy(() -> decoder.decode(invalid)).isInstanceOf(JwtException.class);
        }
        var other = configuration.signingKey(properties, environment, new DefaultResourceLoader());
        assertThatThrownBy(() -> decoder.decode(token(configuration.jwtEncoder(other), "finlab", "finlab-api", "1", now.plusSeconds(900))))
            .isInstanceOf(JwtException.class);
    }

    @Test void configuredPemKeysRemainUsableOutsideLocalProfile(@TempDir Path directory) throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        Path privatePath = directory.resolve("private.pem");
        Path publicPath = directory.resolve("public.pem");
        Files.writeString(privatePath, pem("PRIVATE KEY", pair.getPrivate().getEncoded()), StandardCharsets.US_ASCII);
        Files.writeString(publicPath, pem("PUBLIC KEY", pair.getPublic().getEncoded()), StandardCharsets.US_ASCII);
        var configured = new JwtProperties("finlab", "finlab-api", Duration.ofMinutes(15), privatePath.toUri().toString(), publicPath.toUri().toString());
        var key = configuration.signingKey(configured, new MockEnvironment(), new DefaultResourceLoader());
        assertThat(key.toRSAPublicKey()).isEqualTo(pair.getPublic());
        var hash = configuration.passwordEncoder().encode("temporary-password");
        assertThat(hash).startsWith("{bcrypt}$2a$12$");
        assertThat(configuration.passwordEncoder().matches("temporary-password", hash)).isTrue();
        assertThat(configuration.passwordEncoder().matches("wrong", hash)).isFalse();
    }

    @Test void rejectsMismatchedAndUndersizedConfiguredKeys(@TempDir Path directory) throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var first = generator.generateKeyPair();
        var second = generator.generateKeyPair();
        Path privatePath = directory.resolve("private.pem");
        Path publicPath = directory.resolve("public.pem");
        Files.writeString(privatePath, pem("PRIVATE KEY", first.getPrivate().getEncoded()));
        Files.writeString(publicPath, pem("PUBLIC KEY", second.getPublic().getEncoded()));
        var configured = new JwtProperties(null, null, null, privatePath.toUri().toString(), publicPath.toUri().toString());
        assertThatThrownBy(() -> configuration.signingKey(configured, new MockEnvironment(), new DefaultResourceLoader()))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("match");
        generator.initialize(1024);
        var undersized = generator.generateKeyPair();
        Files.writeString(privatePath, pem("PRIVATE KEY", undersized.getPrivate().getEncoded()));
        Files.writeString(publicPath, pem("PUBLIC KEY", undersized.getPublic().getEncoded()));
        assertThatThrownBy(() -> configuration.signingKey(configured, new MockEnvironment(), new DefaultResourceLoader()))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("2048");
    }

    @Test void requiresPkcs8PemPrivateKeyAndRejectsMalformedPem(@TempDir Path directory) throws Exception {
        Path privatePath = directory.resolve("private.pem");
        Path publicPath = directory.resolve("public.pem");
        Files.writeString(privatePath, "-----BEGIN RSA PRIVATE KEY-----\ninvalid\n-----END RSA PRIVATE KEY-----\n");
        Files.writeString(publicPath, "-----BEGIN PUBLIC KEY-----\ninvalid\n-----END PUBLIC KEY-----\n");
        var configured = new JwtProperties(null, null, null, privatePath.toUri().toString(), publicPath.toUri().toString());
        assertThatThrownBy(() -> configuration.signingKey(configured, new MockEnvironment(), new DefaultResourceLoader()))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("PKCS#8");
    }

    private static String token(JwtEncoder encoder, String issuer, String audience, String subject, Instant expiration) {
        var claims = JwtClaimsSet.builder().issuer(issuer).audience(List.of(audience)).subject(subject);
        if (expiration != null) claims.expiresAt(expiration);
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims.build())).getTokenValue();
    }
    private static String pem(String label, byte[] encoded) {
        return "-----BEGIN " + label + "-----\n" + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded)
            + "\n-----END " + label + "-----\n";
    }
}

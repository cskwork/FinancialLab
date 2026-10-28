package io.github.cskwork.finlab.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("finlab.jwt")
public record JwtProperties(String issuer, String audience, Duration ttl, String privateKeyLocation, String publicKeyLocation) {
    public JwtProperties {
        issuer = issuer == null ? "finlab" : issuer;
        audience = audience == null ? "finlab-api" : audience;
        ttl = ttl == null ? Duration.ofMinutes(15) : ttl;
        if (issuer.isBlank() || audience.isBlank() || ttl.isNegative() || ttl.isZero() || ttl.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalArgumentException("JWT issuer, audience and a positive TTL of at most one hour are required");
        }
    }
}

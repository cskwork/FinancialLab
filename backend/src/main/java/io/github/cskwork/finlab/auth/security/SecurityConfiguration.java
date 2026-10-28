package io.github.cskwork.finlab.auth.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import io.github.cskwork.finlab.auth.config.JwtProperties;
import io.github.cskwork.finlab.common.error.Problems;
import io.github.cskwork.finlab.config.FinLabProperties;
import io.github.cskwork.finlab.config.ClockConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.ContentSecurityPolicyHeaderWriter;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@Import(ClockConfiguration.class)
@EnableConfigurationProperties({JwtProperties.class, FinLabProperties.class})
public class SecurityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", new BCryptPasswordEncoder(12)));
    }

    @Bean
    RSAKey signingKey(JwtProperties properties, Environment environment, ResourceLoader resources) throws Exception {
        if (hasText(properties.privateKeyLocation()) != hasText(properties.publicKeyLocation())) {
            throw new IllegalStateException("Configure both JWT private and public key PEM resources");
        }
        if (hasText(properties.privateKeyLocation()) && hasText(properties.publicKeyLocation())) {
            RSAPrivateKey privateKey;
            RSAPublicKey publicKey;
            try (var privateInput = resources.getResource(properties.privateKeyLocation()).getInputStream();
                 var publicInput = resources.getResource(properties.publicKeyLocation()).getInputStream()) {
                privateKey = Objects.requireNonNull(RsaKeyConverters.pkcs8().convert(privateInput));
                publicKey = Objects.requireNonNull(RsaKeyConverters.x509().convert(publicInput));
            }
            if (!privateKey.getModulus().equals(publicKey.getModulus()) || publicKey.getModulus().bitLength() < 2048) {
                throw new IllegalStateException("JWT keys must match and use RSA with at least 2048 bits");
            }
            return new RSAKey.Builder(publicKey).privateKey(privateKey).keyID("finlab-rsa").build();
        }
        if (!environment.acceptsProfiles(Profiles.of("local", "test"))) {
            throw new IllegalStateException("Configure FINLAB_JWT_PRIVATE_KEY_LOCATION and FINLAB_JWT_PUBLIC_KEY_LOCATION PEM resource locations");
        }
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        return new RSAKey.Builder((RSAPublicKey) pair.getPublic()).privateKey(pair.getPrivate()).keyID("ephemeral-local").build();
    }

    private static boolean hasText(String value) { return value != null && !value.isBlank(); }
    @Bean
    JwtEncoder jwtEncoder(RSAKey key) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    }

    @Bean
    JwtDecoder jwtDecoder(RSAKey key, JwtProperties properties) throws Exception {
        var decoder = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).signatureAlgorithm(SignatureAlgorithm.RS256).build();
        var audience = new JwtClaimValidator<List<String>>("aud", values -> values != null && values.contains(properties.audience()));
        var subject = new JwtClaimValidator<String>("sub", value -> value != null && value.matches("[1-9][0-9]{0,17}"));
        var expiration = new JwtClaimValidator<java.time.Instant>("exp", Objects::nonNull);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(properties.issuer()), audience, subject, expiration));
        return decoder;
    }

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, ObjectMapper mapper) throws Exception {
        var authentication = new JwtAuthenticationConverter();
        authentication.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = jwt.getClaimAsString("role");
            return Set.of("USER", "ADMIN").contains(role == null ? "" : role)
                ? List.of(new SimpleGrantedAuthority("ROLE_" + role)) : List.of();
        });
        http.csrf(AbstractHttpConfigurer::disable).cors(Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .requestCache(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/v1/auth/login", "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                    "/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers("/v1/**").hasAnyRole("USER", "ADMIN")
                .anyRequest().denyAll())
            .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.jwtAuthenticationConverter(authentication))
                .authenticationEntryPoint((request, response, failure) -> writeProblem(mapper, request, response, 401, "UNAUTHORIZED", "인증이 필요합니다"))
                .accessDeniedHandler((request, response, failure) -> writeProblem(mapper, request, response, 403, "FORBIDDEN", "접근 권한이 없습니다")))
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, failure) -> writeProblem(mapper, request, response, 401, "UNAUTHORIZED", "인증이 필요합니다"))
                .accessDeniedHandler((request, response, failure) -> writeProblem(mapper, request, response, 403, "FORBIDDEN", "접근 권한이 없습니다")))
            .headers(headers -> headers
                .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(SecurityConfiguration::isSwaggerRequest,
                    new ContentSecurityPolicyHeaderWriter("default-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; frame-ancestors 'none'; base-uri 'self'")))
                .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(request -> !isSwaggerRequest(request),
                    new ContentSecurityPolicyHeaderWriter("default-src 'self'; frame-ancestors 'none'; base-uri 'self'")))
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                .frameOptions(frame -> frame.deny()));
        return http.build();
    }

    private static void writeProblem(ObjectMapper mapper, jakarta.servlet.http.HttpServletRequest request, HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        if (status == 401) response.setHeader("WWW-Authenticate", "Bearer");
        var problem = Problems.create(status, code, message, Map.of());
        problem.setInstance(java.net.URI.create(request.getRequestURI()));
        mapper.writeValue(response.getWriter(), problem);
    }

    private static boolean isSwaggerRequest(jakarta.servlet.http.HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith("/swagger-ui/") || path.equals("/swagger-ui.html");
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(FinLabProperties properties) {
        var configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.cors().allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id", "Idempotency-Key"));
        configuration.setExposedHeaders(List.of("X-Request-Id", "Retry-After", "X-Total-Count"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}

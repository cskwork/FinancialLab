package io.github.cskwork.finlab.auth.web;

import io.github.cskwork.finlab.auth.application.AuthService;
import io.github.cskwork.finlab.common.error.ApiException;
import io.github.cskwork.finlab.common.error.ApiExceptionHandler;
import io.github.cskwork.finlab.common.web.RequestIdFilter;
import io.github.cskwork.finlab.auth.security.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({SecurityConfiguration.class, ApiExceptionHandler.class, RequestIdFilter.class})
@ActiveProfiles("test")
class AuthControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AuthService auth;

    @Test void acceptsEmailAndPasswordAndDoesNotReturnCredentials() throws Exception {
        when(auth.login("demo@finlab.app", "temporary-secret", "127.0.0.1"))
            .thenReturn(new AuthService.LoginResult(1, "demo@finlab.app", "USER", "signed-token"));
        mvc.perform(post("/v1/auth/login").header("X-Request-Id", "test-login-1").contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"demo@finlab.app","password":"temporary-secret"}
                """))
            .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(1))
            .andExpect(jsonPath("$.token").value("signed-token")).andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(header().string("X-Request-Id", "test-login-1"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("X-Frame-Options", "DENY"));
        verify(auth).login("demo@finlab.app", "temporary-secret", "127.0.0.1");
    }

    @Test void acceptsPasswordsAtTheAsciiAndUtf8ByteBoundary() throws Exception {
        when(auth.login(anyString(), anyString(), anyString()))
            .thenReturn(new AuthService.LoginResult(1, "demo@finlab.app", "USER", "signed-token"));
        for (String password : new String[]{"a".repeat(72), "가".repeat(24)}) {
            mvc.perform(post("/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"demo@finlab.app","password":"%s"}
                """.formatted(password)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.password").doesNotExist());
            verify(auth).login("demo@finlab.app", password, "127.0.0.1");
        }
    }

    @Test void rejectsPasswordsBeyondTheAsciiAndUtf8ByteBoundary() throws Exception {
        for (String password : new String[]{"a".repeat(73), "가".repeat(36)}) {
            mvc.perform(post("/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"demo@finlab.app","password":"%s"}
                """.formatted(password)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(content().string(not(containsString(password))));
        }
        verifyNoInteractions(auth);
    }

    @Test void missingPasswordIsFieldValidationProblemWithoutLeakingCredentials() throws Exception {
        mvc.perform(post("/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"demo@finlab.app"}
                """))
            .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.status").value(400)).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").isString()).andExpect(jsonPath("$.details.fieldErrors.password").isString());
        verifyNoInteractions(auth);
    }

    @Test void invalidEmailAndMalformedJsonAre400() throws Exception {
        mvc.perform(post("/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""
                {"email":"bad-email","password":"secret"}
                """))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.fieldErrors.email").isString());
        mvc.perform(post("/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(auth);
    }

    @Test void wrongCredentialsReturnGeneric401() throws Exception {
        when(auth.login(anyString(), anyString(), anyString())).thenThrow(new ApiException(401, "INVALID_CREDENTIALS", "인증에 실패했습니다"));
        mvc.perform(post("/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""
                {"email":"demo@finlab.app","password":"wrong-secret"}
                """))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
            .andExpect(content().string(not(containsString("wrong-secret"))));
    }

    @Test void bearerApiRejectsMissingInvalidAndInsufficientAuthorityTokens() throws Exception {
        mvc.perform(get("/v1/portfolios").param("userId", "1"))
            .andExpect(status().isUnauthorized()).andExpect(header().string("WWW-Authenticate", "Bearer"))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/v1/portfolios").header("Authorization", "Bearer invalid"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        mvc.perform(get("/v1/portfolios").with(jwt().jwt(token -> token.subject("1"))))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test void authenticatedUnknownResourceIs404AndUnexpectedErrorsAreSanitized() throws Exception {
        mvc.perform(get("/v1/unknown").with(jwt().jwt(token -> token.subject("1"))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        when(auth.login(anyString(), anyString(), anyString())).thenThrow(new IllegalStateException("private-secret"));
        mvc.perform(post("/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""
                {"email":"demo@finlab.app","password":"temporary-secret"}
                """))
            .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(content().string(not(containsString("private-secret"))));
    }

    @Test void usesTheSocketAddressAndIgnoresSpoofedForwardedAddress() throws Exception {
        when(auth.login(anyString(), anyString(), anyString()))
            .thenReturn(new AuthService.LoginResult(1, "demo@finlab.app", "USER", "signed-token"));
        mvc.perform(post("/v1/auth/login").header("X-Forwarded-For", "198.51.100.123")
                .with(request -> { request.setRemoteAddr("192.0.2.10"); return request; })
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"demo@finlab.app","password":"temporary-secret"}
                """))
            .andExpect(status().isOk());
        verify(auth).login("demo@finlab.app", "temporary-secret", "192.0.2.10");
    }

    @Test void rateLimitAndBusyResponsesIncludeRetryAfter() throws Exception {
        when(auth.login(anyString(), anyString(), anyString()))
            .thenThrow(new ApiException(429, "AUTHENTICATION_RATE_LIMITED", "잠시 후 다시 시도하세요", 30))
            .thenThrow(new ApiException(503, "AUTHENTICATION_BUSY", "잠시 후 다시 시도하세요", 1));
        mvc.perform(post("/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"demo@finlab.app","password":"temporary-secret"}
                """))
            .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "30"))
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_RATE_LIMITED"));
        mvc.perform(post("/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"demo@finlab.app","password":"temporary-secret"}
                """))
            .andExpect(status().isServiceUnavailable()).andExpect(header().string("Retry-After", "1"))
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_BUSY"));
    }

    @Test void relaxesInlineStylesOnlyForSwaggerPages() throws Exception {
        mvc.perform(get("/swagger-ui/index.html"))
            .andExpect(header().string("Content-Security-Policy", containsString("style-src 'self' 'unsafe-inline'")));
        mvc.perform(get("/v1/portfolios"))
            .andExpect(header().string("Content-Security-Policy", not(containsString("unsafe-inline"))));
    }

    @Test void allowsConfiguredCorsAndRejectsUntrustedOrigin() throws Exception {
        mvc.perform(options("/v1/portfolios").header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type,x-request-id,idempotency-key"))
            .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mvc.perform(options("/v1/portfolios").header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "POST"))
            .andExpect(status().isForbidden()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}

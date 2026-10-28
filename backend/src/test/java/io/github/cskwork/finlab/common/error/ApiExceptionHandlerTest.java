package io.github.cskwork.finlab.common.error;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;

class ApiExceptionHandlerTest {
    private final ApiExceptionHandler advice = new ApiExceptionHandler();
    @Test void frameworkErrorResponseKeepsItsStatusAndSafeExtensionFields() throws Exception {
        var request = new ServletWebRequest(new MockHttpServletRequest());
        var response = advice.handleException(new ResponseStatusException(HttpStatus.GONE, "Resource expired"), request);
        assertThat(response.getStatusCode().value()).isEqualTo(410);
        var problem = (ProblemDetail) response.getBody();
        assertThat(problem.getProperties()).containsEntry("code", "HTTP_410").containsEntry("message", "Resource expired");
        assertThat(problem.getProperties()).doesNotContainKey("details");
    }
    @Test void integrityConflictsHaveTheirOwnCodeAndLogCauseAtWarn() {
        var logger = (Logger) LoggerFactory.getLogger(ApiExceptionHandler.class);
        var appender = new ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            var cause = new IllegalArgumentException("constraint detail");
            var problem = advice.dataIntegrity(new DataIntegrityViolationException("private sql", cause));
            assertThat(problem.getStatus()).isEqualTo(409);
            assertThat(problem.getType().toString()).isEqualTo("urn:finlab:problem:data-integrity");
            assertThat(problem.getProperties()).containsEntry("code", "DATA_INTEGRITY").doesNotContainKey("details");
            assertThat(problem.getDetail()).doesNotContain("private sql", "constraint detail");
            assertThat(appender.list).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getThrowableProxy().getCause().getMessage()).isEqualTo("constraint detail");
            });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
    @Test void persistenceConflictsAre409AndThrottlingIncludesRetryAfter() {
        var conflict = advice.conflict(new CannotAcquireLockException("private sql"));
        assertThat(conflict.getStatus()).isEqualTo(409);
        assertThat(conflict.getProperties()).containsEntry("code", "CONCURRENT_MODIFICATION");
        assertThat(conflict.getDetail()).doesNotContain("private sql");
        var response = advice.api(new ApiException(429, "RATE_LIMITED", "Try later", 12),
                new ServletWebRequest(new MockHttpServletRequest()));
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("12");
        assertThat(response.getStatusCode().value()).isEqualTo(429);
    }
}

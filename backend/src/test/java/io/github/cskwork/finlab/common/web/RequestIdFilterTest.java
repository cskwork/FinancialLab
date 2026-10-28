package io.github.cskwork.finlab.common.web;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;

class RequestIdFilterTest {
    private final RequestIdFilter filter = new RequestIdFilter();
    @Test void echoesSafeIdOnlyDuringRequest() throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        request.addHeader("X-Request-Id", "safe-id_123.4");
        filter.doFilter(request, response, (req, res) -> assertThat(MDC.get("requestId")).isEqualTo("safe-id_123.4"));
        assertThat(response.getHeader("X-Request-Id")).isEqualTo("safe-id_123.4");
        assertThat(MDC.get("requestId")).isNull();
    }
    @Test void replacesMissingOrUnsafeIdAndClearsOnFailure() throws Exception {
        for (String supplied : new String[]{"", "unsafe\r\nheader", "x".repeat(129)}) {
            var request = new MockHttpServletRequest();
            var response = new MockHttpServletResponse();
            request.addHeader("X-Request-Id", supplied);
            assertThatThrownBy(() -> filter.doFilter(request, response, (req, res) -> { throw new ServletException("failed"); }))
                .isInstanceOf(ServletException.class);
            assertThat(response.getHeader("X-Request-Id")).matches("[0-9a-f-]{36}");
            assertThat(MDC.get("requestId")).isNull();
        }
    }
}

package io.github.cskwork.finlab.common.error;

import java.net.URI;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.slf4j.MDC;

public final class Problems {
    private Problems() {}
    public static ProblemDetail create(int status, String code, String message, Map<String, ?> details) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(status), message);
        problem.setType(URI.create("urn:finlab:problem:" + code.toLowerCase(java.util.Locale.ROOT).replace('_', '-')));
        problem.setProperty("code", code);
        problem.setProperty("message", message);
        if (details != null && !details.isEmpty()) problem.setProperty("details", details);
        String requestId = MDC.get("requestId");
        if (requestId != null) problem.setProperty("requestId", requestId);
        return problem;
    }
}

package io.github.cskwork.finlab.common.error;

import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PessimisticLockException;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Object> api(ApiException failure, WebRequest request) {
        var headers = new HttpHeaders();
        if (failure.retryAfterSeconds() != null) headers.set(HttpHeaders.RETRY_AFTER, failure.retryAfterSeconds().toString());
        return createResponseEntity(Problems.create(failure.status(), failure.code(), failure.getMessage(), Map.of()),
                headers, HttpStatusCode.valueOf(failure.status()), request);
    }

    @Override protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException failure,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        failure.getBindingResult().getFieldErrors().forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        failure.getBindingResult().getGlobalErrors().forEach(error -> fields.putIfAbsent("request", error.getDefaultMessage()));
        return createResponseEntity(Problems.create(status.value(), "VALIDATION_ERROR", "입력 데이터가 유효하지 않습니다",
                Map.of("fieldErrors", fields)), headers, status, request);
    }

    @Override protected ResponseEntity<Object> handleExceptionInternal(Exception failure, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var problem = body instanceof ProblemDetail detail ? detail
                : failure instanceof ErrorResponse response ? response.getBody() : ProblemDetail.forStatus(status);
        String code = switch (status.value()) {
            case 400 -> "VALIDATION_ERROR";
            case 404 -> "NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 406 -> "NOT_ACCEPTABLE";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            default -> "HTTP_" + status.value();
        };
        String message = status.is5xxServerError() ? "요청을 처리하지 못했습니다"
                : status.value() == 400 ? "요청 형식이 올바르지 않습니다"
                : problem.getDetail() == null ? problem.getTitle() : problem.getDetail();
        problem.setDetail(message);
        problem.setProperty("code", code);
        problem.setProperty("message", message);
        return super.handleExceptionInternal(failure, problem, headers, status, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail malformed(ConstraintViolationException failure) {
        return Problems.create(400, "VALIDATION_ERROR", "요청 형식이 올바르지 않습니다", Map.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail dataIntegrity(DataIntegrityViolationException failure) {
        log.warn("API data integrity conflict", failure);
        return Problems.create(409, "DATA_INTEGRITY", "데이터 제약 조건을 위반했습니다", Map.of());
    }

    @ExceptionHandler({OptimisticLockingFailureException.class,
            PessimisticLockingFailureException.class, LockTimeoutException.class, PessimisticLockException.class})
    public ProblemDetail conflict(Exception failure) {
        return Problems.create(409, "CONCURRENT_MODIFICATION", "데이터 충돌이 발생했습니다. 다시 시도하세요", Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail internal(Exception failure) {
        // HTTP bodies, headers and credential objects are never attached to log events.
        log.error("Unexpected API failure", failure);
        return Problems.create(500, "INTERNAL_ERROR", "요청을 처리하지 못했습니다", Map.of());
    }
}

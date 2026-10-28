package io.github.cskwork.finlab.common.error;

public class ApiException extends RuntimeException {
    private final int status;
    private final String code;
    private final Long retryAfterSeconds;
    public ApiException(int status, String code, String message) {
        this(status, code, message, null);
    }
    public ApiException(int status, String code, String message, long retryAfterSeconds) {
        this(status, code, message, Long.valueOf(retryAfterSeconds));
    }
    private ApiException(int status, String code, String message, Long retryAfterSeconds) {
        super(message);
        this.status = status;
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
    }
    public Long retryAfterSeconds() { return retryAfterSeconds; }
    public int status() { return status; }
    public String code() { return code; }
    public static ApiException notFound() { return new ApiException(404, "NOT_FOUND", "리소스를 찾을 수 없습니다"); }
    public static ApiException validation(String message) { return new ApiException(422, "VALIDATION_ERROR", message); }
}

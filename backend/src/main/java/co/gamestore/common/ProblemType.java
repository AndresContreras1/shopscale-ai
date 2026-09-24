package co.gamestore.common;

import org.springframework.http.HttpStatus;

/**
 * The catalog of errors this API can return, as RFC 9457 problem types.
 *
 * <p>Each entry owns a stable {@code type} URI and a stable {@code code}. A client branches on the
 * code, never on the wording of a message, so the text can be improved or translated without
 * breaking anyone. A status code alone is not enough: three different conflicts all return 409.
 */
public enum ProblemType {

    BAD_REQUEST("bad-request", "Bad request", HttpStatus.BAD_REQUEST),
    VALIDATION_FAILED("validation-failed", "Validation failed", HttpStatus.BAD_REQUEST),
    PASSWORD_REJECTED("password-rejected", "Password rejected", HttpStatus.BAD_REQUEST),
    TOKEN_INVALID("token-invalid", "Link no longer valid", HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED("unauthenticated", "Authentication required", HttpStatus.UNAUTHORIZED),
    MFA_REQUIRED("mfa-required", "Second factor required", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("forbidden", "Insufficient permissions", HttpStatus.FORBIDDEN),
    NOT_FOUND("not-found", "Resource not found", HttpStatus.NOT_FOUND),
    BUSINESS_RULE("business-rule", "Business rule violated", HttpStatus.CONFLICT),
    CONCURRENT_UPDATE("concurrent-update", "Concurrent update", HttpStatus.CONFLICT),
    RATE_LIMITED("rate-limited", "Too many requests", HttpStatus.TOO_MANY_REQUESTS),
    INTERNAL("internal-error", "Unexpected error", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String title;
    private final HttpStatus status;

    ProblemType(String code, String title, HttpStatus status) {
        this.code = code;
        this.title = title;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public String title() {
        return title;
    }

    public HttpStatus status() {
        return status;
    }


}

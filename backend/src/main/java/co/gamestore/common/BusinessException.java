package co.gamestore.common;

/**
 * A request that is well formed but violates a business rule (e.g. not enough stock).
 * Mapped to HTTP 409 Conflict.
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}

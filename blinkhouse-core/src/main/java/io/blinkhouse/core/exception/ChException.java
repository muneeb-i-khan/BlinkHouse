package io.blinkhouse.core.exception;

/**
 * Base exception for all ClickORM runtime errors.
 *
 * <p>Subtypes represent distinct failure modes so callers can catch selectively.
 * The {@code errorCode} field carries the raw ClickHouse server error code when the
 * exception originates from a server response; {@code -1} when unknown or not applicable.
 */
public class ChException extends RuntimeException {

    private final int errorCode;

    public ChException(String message) {
        super(message);
        this.errorCode = -1;
    }

    public ChException(String message, int errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public ChException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = -1;
    }

    public ChException(String message, int errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /** Raw ClickHouse server error code, or {@code -1} if not a server error. */
    public int getErrorCode() {
        return errorCode;
    }
}

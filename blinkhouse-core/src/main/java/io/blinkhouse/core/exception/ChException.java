package io.blinkhouse.core.exception;

/**
 * Base exception for all ClickHouse-related errors thrown by BlinkHouse.
 *
 * <p>Carries the raw server error code when one is available ({@code -1} otherwise).
 * Prefer the typed subclasses ({@link ChTimeoutException}, {@link ChSyntaxException}, etc.)
 * for programmatic error handling.
 */
public class ChException extends RuntimeException {

    private final int errorCode;

    /** Constructs with a message and no server error code. */
    public ChException(String message) {
        super(message);
        this.errorCode = -1;
    }

    /** Constructs with a message and a ClickHouse server error code. */
    public ChException(String message, int errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    /** Constructs with a message and a cause. */
    public ChException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = -1;
    }

    /** Constructs with a message, a server error code, and a cause. */
    public ChException(String message, int errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * Returns the ClickHouse server error code, or {@code -1} if not applicable
     * (e.g. network-layer or client-side errors).
     */
    public int getErrorCode() {
        return errorCode;
    }
}

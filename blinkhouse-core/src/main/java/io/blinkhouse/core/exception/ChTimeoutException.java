package io.blinkhouse.core.exception;

/**
 * Thrown when a ClickHouse query exceeds its execution time limit (error code 159)
 * or when a socket-level timeout occurs.
 */
public final class ChTimeoutException extends ChException {

    /** Constructs with the server error message and code. */
    public ChTimeoutException(String message, int errorCode) {
        super(message, errorCode);
    }

    /** Constructs wrapping a network-level timeout. */
    public ChTimeoutException(String message, Throwable cause) {
        super(message, ChErrorCode.SOCKET_TIMEOUT, cause);
    }
}

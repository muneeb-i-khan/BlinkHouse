package io.blinkhouse.core.exception;

/**
 * Thrown when a network-level connection error prevents communication with ClickHouse.
 */
public final class ChConnectionException extends ChException {

    /** Constructs wrapping the underlying network error. */
    public ChConnectionException(String message, Throwable cause) {
        super(message, ChErrorCode.NETWORK_ERROR, cause);
    }

    /** Constructs with a message and error code (e.g. {@link ChErrorCode#NO_FREE_CONNECTION}). */
    public ChConnectionException(String message, int errorCode) {
        super(message, errorCode);
    }
}

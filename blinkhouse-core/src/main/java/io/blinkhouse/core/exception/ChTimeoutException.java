package io.blinkhouse.core.exception;

/** Thrown when ClickHouse reports a timeout or the socket/connection times out. */
public final class ChTimeoutException extends ChException {

    public ChTimeoutException(String message) {
        super(message, ChErrorCode.TIMEOUT_EXCEEDED);
    }

    public ChTimeoutException(String message, int errorCode) {
        super(message, errorCode);
    }

    public ChTimeoutException(String message, Throwable cause) {
        super(message, ChErrorCode.TIMEOUT_EXCEEDED, cause);
    }

    public ChTimeoutException(String message, int errorCode, Throwable cause) {
        super(message, errorCode, cause);
    }
}

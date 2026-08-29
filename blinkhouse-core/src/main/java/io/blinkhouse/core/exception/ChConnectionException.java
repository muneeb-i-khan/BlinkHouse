package io.blinkhouse.core.exception;

/** Thrown when the network connection to ClickHouse fails or is refused. */
public final class ChConnectionException extends ChException {

    public ChConnectionException(String message) {
        super(message, ChErrorCode.NETWORK_ERROR);
    }

    public ChConnectionException(String message, int errorCode) {
        super(message, errorCode);
    }

    public ChConnectionException(String message, Throwable cause) {
        super(message, ChErrorCode.NETWORK_ERROR, cause);
    }

    public ChConnectionException(String message, int errorCode, Throwable cause) {
        super(message, errorCode, cause);
    }
}

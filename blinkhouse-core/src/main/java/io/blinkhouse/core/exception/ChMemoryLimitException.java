package io.blinkhouse.core.exception;

/** Thrown when ClickHouse rejects a query due to memory limits (error code 241). */
public final class ChMemoryLimitException extends ChException {

    public ChMemoryLimitException(String message) {
        super(message, ChErrorCode.MEMORY_LIMIT_EXCEEDED);
    }

    public ChMemoryLimitException(String message, Throwable cause) {
        super(message, ChErrorCode.MEMORY_LIMIT_EXCEEDED, cause);
    }
}

package io.blinkhouse.core.exception;

/**
 * Thrown when ClickHouse aborts a query due to memory limit exceeded (error code 241).
 *
 * <p>This exception is retryable: {@link io.blinkhouse.core.write.ErrorClassifier}
 * maps it to {@code RETRYABLE_HALVE_BATCH} — the batch writer halves the batch size
 * and re-tries the second half.
 */
public final class ChMemoryLimitException extends ChException {

    /** Constructs with the server error message. */
    public ChMemoryLimitException(String message) {
        super(message, ChErrorCode.MEMORY_LIMIT_EXCEEDED);
    }
}

package io.blinkhouse.core.exception;

/**
 * Thrown when ClickHouse rejects an insert because a partition has too many active
 * data parts (error code 252).
 *
 * <p>This is a back-pressure signal. {@link io.blinkhouse.core.write.ErrorClassifier}
 * maps it to {@code RETRYABLE_HALVE_BATCH} — the batch writer reduces batch size
 * and retries with exponential back-off to let ClickHouse merge parts.
 */
public final class ChTooManyPartsException extends ChException {

    /** Constructs with the server error message. */
    public ChTooManyPartsException(String message) {
        super(message, ChErrorCode.TOO_MANY_PARTS);
    }
}

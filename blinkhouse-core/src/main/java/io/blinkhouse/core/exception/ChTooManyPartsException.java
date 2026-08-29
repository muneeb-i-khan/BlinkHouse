package io.blinkhouse.core.exception;

/**
 * Thrown when ClickHouse reports TOO_MANY_PARTS (error code 252).
 *
 * <p>This is a retryable error — the server is merging parts in the background.
 * {@link io.blinkhouse.core.write.ErrorClassifier} applies a longer backoff and
 * halves the batch size on retry.
 */
public final class ChTooManyPartsException extends ChException {

    public ChTooManyPartsException(String message) {
        super(message, ChErrorCode.TOO_MANY_PARTS);
    }

    public ChTooManyPartsException(String message, Throwable cause) {
        super(message, ChErrorCode.TOO_MANY_PARTS, cause);
    }
}

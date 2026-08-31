package io.blinkhouse.core.exception;

/**
 * Thrown by {@link io.blinkhouse.core.write.BatchWriter} when the buffer is full,
 * the backpressure policy is {@code BLOCK}, and the acquire timeout expires.
 */
public final class ChBackpressureException extends ChException {

    public ChBackpressureException(String message) {
        super(message);
    }
}

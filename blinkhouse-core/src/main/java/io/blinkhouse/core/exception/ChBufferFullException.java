package io.blinkhouse.core.exception;

/**
 * Thrown by {@link io.blinkhouse.core.write.BatchWriter} when the ring buffer is full
 * and the backpressure policy is {@code FAIL}.
 */
public final class ChBufferFullException extends ChException {

    public ChBufferFullException(String message) {
        super(message);
    }
}

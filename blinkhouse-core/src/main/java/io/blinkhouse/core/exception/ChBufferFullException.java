package io.blinkhouse.core.exception;

/**
 * Thrown when the {@link io.blinkhouse.core.write.BatchWriter} buffer is full and
 * the backpressure policy is {@link io.blinkhouse.core.write.BackpressurePolicy#FAIL}.
 */
public final class ChBufferFullException extends ChException {

    /** Constructs with a descriptive message. */
    public ChBufferFullException(String message) {
        super(message);
    }
}

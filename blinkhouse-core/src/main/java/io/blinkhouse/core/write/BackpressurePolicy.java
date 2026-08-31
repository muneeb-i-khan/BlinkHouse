package io.blinkhouse.core.write;

/**
 * Policy applied when the {@link BatchWriter} buffer is full.
 */
public enum BackpressurePolicy {

    /**
     * Block the calling thread until space is available.
     * Safe for controlled producers; risks head-of-line blocking under sustained overload.
     */
    BLOCK,

    /**
     * Evict the oldest buffered item to make room for the new one.
     * Preserves recency at the cost of dropping the oldest data.
     */
    DROP_OLDEST,

    /**
     * Throw a {@link io.blinkhouse.core.exception.ChBufferFullException} immediately.
     * Lets the caller decide how to handle back-pressure.
     */
    FAIL
}

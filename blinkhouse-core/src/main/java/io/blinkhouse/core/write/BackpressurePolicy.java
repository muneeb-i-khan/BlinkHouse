package io.blinkhouse.core.write;

/**
 * What {@link BatchWriter} does when a producer calls {@code add()} and the ring
 * buffer is full.
 */
public enum BackpressurePolicy {

    /**
     * Block the producer thread until space becomes available or the acquire timeout
     * expires, then throw {@link io.blinkhouse.core.exception.ChBackpressureException}.
     */
    BLOCK,

    /**
     * Evict the oldest row from the buffer to make room, increment the dropped-row
     * metric, and accept the new row without blocking.
     */
    DROP_OLDEST,

    /**
     * Throw {@link io.blinkhouse.core.exception.ChBufferFullException} immediately
     * without any blocking or eviction.
     */
    FAIL
}

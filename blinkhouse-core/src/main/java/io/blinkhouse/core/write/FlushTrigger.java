package io.blinkhouse.core.write;

import java.time.Duration;
import java.time.Instant;

/**
 * Determines when the {@link BatchWriter} flusher should drain the buffer.
 *
 * <p>A flush is triggered when <em>any</em> of the three conditions fires:
 * <ol>
 *   <li>Accumulated row count ≥ {@code maxRows}</li>
 *   <li>Accumulated byte estimate ≥ {@code maxBytes}</li>
 *   <li>Time since last flush ≥ {@code flushInterval}</li>
 * </ol>
 */
public final class FlushTrigger {

    private final int maxRows;
    private final long maxBytes;
    private final Duration flushInterval;

    private volatile Instant lastFlush = Instant.now();

    public FlushTrigger(int maxRows, long maxBytes, Duration flushInterval) {
        this.maxRows = maxRows;
        this.maxBytes = maxBytes;
        this.flushInterval = flushInterval;
    }

    /** Returns {@code true} if any flush condition is met. */
    public boolean shouldFlush(int bufferedRows, long bufferedBytes) {
        if (bufferedRows >= maxRows) {
            return true;
        }
        if (bufferedBytes >= maxBytes) {
            return true;
        }
        return Duration.between(lastFlush, Instant.now()).compareTo(flushInterval) >= 0;
    }

    /** Must be called by the flusher after each successful or failed flush attempt. */
    public void markFlushed() {
        lastFlush = Instant.now();
    }
}

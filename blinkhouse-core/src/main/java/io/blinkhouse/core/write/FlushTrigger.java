package io.blinkhouse.core.write;

import java.time.Duration;

/**
 * Encapsulates the three flush-trigger conditions for a {@link BatchWriter}.
 *
 * <p>A flush is triggered when <em>any one</em> of the three thresholds is exceeded:
 * <ol>
 *   <li>Row count ≥ {@code maxRows}</li>
 *   <li>Serialised byte size ≥ {@code maxBytes}</li>
 *   <li>Time since last flush ≥ {@code flushInterval}</li>
 * </ol>
 *
 * <p>Instances are not thread-safe; each flusher thread should use its own.
 */
public final class FlushTrigger {

    private final int maxRows;
    private final long maxBytes;
    private final long flushIntervalNanos;
    private long lastFlushedNanos;

    /**
     * Constructs a flush trigger.
     *
     * @param maxRows       flush when the buffer contains at least this many rows
     * @param maxBytes      flush when serialised data reaches this many bytes
     * @param flushInterval flush at least this often regardless of row/byte count
     */
    public FlushTrigger(int maxRows, long maxBytes, Duration flushInterval) {
        this.maxRows = maxRows;
        this.maxBytes = maxBytes;
        this.flushIntervalNanos = flushInterval.toNanos();
        this.lastFlushedNanos = System.nanoTime();
    }

    /**
     * Returns {@code true} if any flush condition is satisfied.
     *
     * @param rows  current buffered row count
     * @param bytes current serialised byte count
     * @return whether a flush should be triggered
     */
    public boolean shouldFlush(int rows, long bytes) {
        if (rows >= maxRows) {
            return true;
        }
        if (bytes >= maxBytes) {
            return true;
        }
        return (System.nanoTime() - lastFlushedNanos) >= flushIntervalNanos;
    }

    /**
     * Resets the interval timer. Must be called after each successful flush.
     */
    public void markFlushed() {
        this.lastFlushedNanos = System.nanoTime();
    }
}

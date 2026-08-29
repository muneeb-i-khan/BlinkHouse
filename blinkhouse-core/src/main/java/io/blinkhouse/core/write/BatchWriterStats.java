package io.blinkhouse.core.write;

import java.util.concurrent.atomic.LongAdder;

/**
 * Live statistics snapshot for a single {@link BatchWriter} instance.
 *
 * <p>Counters are updated by flusher threads using {@link LongAdder} for low contention.
 * Call {@link #snapshot()} to read a consistent point-in-time copy.
 */
public final class BatchWriterStats {

    private final LongAdder rowsInserted    = new LongAdder();
    private final LongAdder rowsDropped     = new LongAdder();
    private final LongAdder rowsDeadLettered = new LongAdder();
    private final LongAdder flushCount      = new LongAdder();
    private final LongAdder retryCount      = new LongAdder();
    private final LongAdder bytesWritten    = new LongAdder();

    /** Point-in-time snapshot of all counters. */
    public record Snapshot(
            long rowsInserted,
            long rowsDropped,
            long rowsDeadLettered,
            long flushCount,
            long retryCount,
            long bytesWritten
    ) { }

    /** Records a successful flush of {@code rows} rows and {@code bytes} bytes. */
    public void recordInserted(long rows, long bytes) {
        rowsInserted.add(rows);
        bytesWritten.add(bytes);
        flushCount.increment();
    }

    /** Records {@code rows} rows silently evicted by DROP_OLDEST backpressure. */
    public void recordDropped(long rows) {
        rowsDropped.add(rows);
    }

    /** Records {@code rows} rows delivered to the dead-letter handler. */
    public void recordDeadLettered(long rows) {
        rowsDeadLettered.add(rows);
    }

    /** Records one retry attempt. */
    public void recordRetry() {
        retryCount.increment();
    }

    /** Returns a consistent point-in-time snapshot. */
    public Snapshot snapshot() {
        return new Snapshot(
                rowsInserted.sum(),
                rowsDropped.sum(),
                rowsDeadLettered.sum(),
                flushCount.sum(),
                retryCount.sum(),
                bytesWritten.sum()
        );
    }
}

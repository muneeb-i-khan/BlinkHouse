package io.blinkhouse.core.write;

import java.util.concurrent.atomic.LongAdder;

/**
 * Lock-free statistics for a {@link BatchWriter} instance.
 *
 * <p>All counters use {@link LongAdder} for high-throughput concurrent updates.
 * Call {@link #snapshot()} to read a consistent point-in-time view.
 */
public final class BatchWriterStats {

    private final LongAdder insertedRows = new LongAdder();
    private final LongAdder insertedBytes = new LongAdder();
    private final LongAdder droppedRows = new LongAdder();
    private final LongAdder deadLetteredRows = new LongAdder();
    private final LongAdder retries = new LongAdder();

    /** Records a successful batch insert. */
    public void recordInserted(long rows, long bytes) {
        insertedRows.add(rows);
        insertedBytes.add(bytes);
    }

    /** Records rows dropped due to {@link BackpressurePolicy#DROP_OLDEST}. */
    public void recordDropped(long rows) {
        droppedRows.add(rows);
    }

    /** Records rows sent to the dead-letter handler. */
    public void recordDeadLettered(long rows) {
        deadLetteredRows.add(rows);
    }

    /** Records a single retry attempt. */
    public void recordRetry() {
        retries.increment();
    }

    /**
     * Returns a point-in-time snapshot of all counters.
     *
     * @return an immutable snapshot
     */
    public Snapshot snapshot() {
        return new Snapshot(
            insertedRows.sum(),
            insertedBytes.sum(),
            droppedRows.sum(),
            deadLetteredRows.sum(),
            retries.sum()
        );
    }

    /**
     * Immutable point-in-time view of {@link BatchWriterStats}.
     *
     * @param insertedRows      total rows successfully delivered to ClickHouse
     * @param insertedBytes     total bytes successfully delivered
     * @param droppedRows       total rows evicted by DROP_OLDEST back-pressure
     * @param deadLetteredRows  total rows delivered to the failure handler
     * @param retries           total retry attempts made
     */
    public record Snapshot(
            long insertedRows,
            long insertedBytes,
            long droppedRows,
            long deadLetteredRows,
            long retries) {
    }
}

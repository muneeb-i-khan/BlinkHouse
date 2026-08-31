package io.blinkhouse.core.write;

import java.time.Duration;

/**
 * Immutable configuration for a {@link BatchWriter}.
 *
 * <p>Use {@link #defaults()} as a starting point, then copy-and-modify:
 * <pre>
 *   BatchWriterConfig cfg = new BatchWriterConfig(
 *       50_000,
 *       16L * 1024 * 1024,
 *       Duration.ofMillis(500),
 *       2,
 *       BackpressurePolicy.DROP_OLDEST,
 *       Duration.ofSeconds(2),
 *       RetryPolicy.defaults(),
 *       (rows, ex, n) -> log.error("dead-letter {} rows", rows.size()),
 *       false, false,
 *       Duration.ofSeconds(10)
 *   );
 * </pre>
 *
 * @param maxRows            flush when the buffer has at least this many rows
 * @param maxBytes           flush when serialised data reaches this many bytes
 * @param flushInterval      flush at least this often regardless of row/byte count
 * @param flusherThreads     number of background flusher threads
 * @param backpressure       what to do when the buffer is full
 * @param acquireTimeout     how long a BLOCK-mode add() may wait before timing out
 * @param retry              retry policy for failed flushes
 * @param failureHandler     dead-letter callback; {@code null} means silently drop
 * @param asyncInsert        pass {@code async_insert=1} to ClickHouse
 * @param waitForAsyncInsert pass {@code wait_for_async_insert=1} to ClickHouse
 * @param drainTimeout       how long {@link BatchWriter#close()} waits for pending rows
 */
public record BatchWriterConfig(
        int maxRows,
        long maxBytes,
        Duration flushInterval,
        int flusherThreads,
        BackpressurePolicy backpressure,
        Duration acquireTimeout,
        RetryPolicy retry,
        BatchFailureHandler<?> failureHandler,
        boolean asyncInsert,
        boolean waitForAsyncInsert,
        Duration drainTimeout) {

    /**
     * Returns the recommended production defaults:
     * 100k rows, 32 MiB, 1s interval, 2 flushers, BLOCK, 5s acquire, 30s drain.
     *
     * @return the default configuration
     */
    public static BatchWriterConfig defaults() {
        return new BatchWriterConfig(
            100_000,
            32L * 1024 * 1024,
            Duration.ofSeconds(1),
            2,
            BackpressurePolicy.BLOCK,
            Duration.ofSeconds(5),
            RetryPolicy.defaults(),
            null,
            false,
            false,
            Duration.ofSeconds(30)
        );
    }
}

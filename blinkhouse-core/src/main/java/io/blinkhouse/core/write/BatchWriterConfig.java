package io.blinkhouse.core.write;

import java.time.Duration;

/**
 * Immutable configuration for {@link BatchWriter}.
 *
 * <p>All fields have production-ready defaults via {@link #defaults()}.
 *
 * @param maxRows            flush when the buffer holds this many rows (default: 100 000)
 * @param maxBytes           flush when the buffer holds this many bytes (default: 32 MiB)
 * @param flushInterval      flush at least this often even if thresholds aren't met (default: 1 s)
 * @param flusherThreads     number of background flusher threads (default: 2)
 * @param backpressure       what to do when the buffer is full (default: BLOCK)
 * @param acquireTimeout     for BLOCK policy — how long to wait before throwing (default: 5 s)
 * @param retry              retry policy for failed flushes (default: RetryPolicy.defaults())
 * @param failureHandler     dead-letter callback; null → log-only default handler
 * @param asyncInsert        send with ClickHouse async_insert=1 (default: false)
 * @param waitForAsyncInsert when asyncInsert=true, wait for server acknowledgement (default: true)
 * @param drainTimeout       time to wait for in-flight batches on close() (default: 30 s)
 */
public record BatchWriterConfig<T>(
        int maxRows,
        long maxBytes,
        Duration flushInterval,
        int flusherThreads,
        BackpressurePolicy backpressure,
        Duration acquireTimeout,
        RetryPolicy retry,
        BatchFailureHandler<T> failureHandler,
        boolean asyncInsert,
        boolean waitForAsyncInsert,
        Duration drainTimeout
) {

    private static final long DEFAULT_MAX_BYTES = 32L * 1024 * 1024; // 32 MiB

    /** Returns a {@code BatchWriterConfig} populated with production-ready defaults. */
    @SuppressWarnings("unchecked")
    public static <T> BatchWriterConfig<T> defaults() {
        return new BatchWriterConfig<>(
                100_000,
                DEFAULT_MAX_BYTES,
                Duration.ofSeconds(1),
                2,
                BackpressurePolicy.BLOCK,
                Duration.ofSeconds(5),
                RetryPolicy.defaults(),
                null,
                false,
                true,
                Duration.ofSeconds(30)
        );
    }
}

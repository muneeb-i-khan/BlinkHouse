package io.blinkhouse.core.write;

import io.blinkhouse.core.exception.ChBackpressureException;
import io.blinkhouse.core.exception.ChBufferFullException;
import io.blinkhouse.core.exception.ChException;
import io.blinkhouse.core.exception.ChExceptionTranslator;
import io.blinkhouse.core.metadata.EntityMetadata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-throughput, asynchronous batch writer for a single ClickHouse table.
 *
 * <h2>Architecture</h2>
 * <ul>
 *   <li>Producers call {@link #add} / {@link #addAll} — these enqueue rows into a
 *       bounded {@link ArrayBlockingQueue} (the ring buffer).</li>
 *   <li>{@code flusherThreads} background threads drain the buffer whenever a flush
 *       trigger fires (row count, byte estimate, or elapsed time).</li>
 *   <li>Each flush serialises the batch with {@link RowBinaryWriter} and POSTs the
 *       raw bytes via {@link HttpClient} to ClickHouse's HTTP endpoint.</li>
 *   <li>On retryable failures the flusher backs off (exponential + jitter) and retries,
 *       halving the batch size for MEMORY_LIMIT_EXCEEDED / TOO_MANY_PARTS.</li>
 *   <li>On terminal failure or exhausted retries the batch is routed to the
 *       {@link BatchFailureHandler} (never silently dropped — NFR-7).</li>
 * </ul>
 *
 * <h2>Thread safety</h2>
 * Multiple producer threads may call {@code add()} concurrently. Each flusher thread
 * owns its drain-and-flush cycle; no synchronisation is needed between flushers beyond
 * the queue itself.
 *
 * <h2>Shutdown</h2>
 * A JVM shutdown hook triggers {@link #close()} automatically. Callers may also call
 * {@code close()} explicitly; subsequent calls are no-ops.
 *
 * @param <T> entity type
 */
public final class BatchWriter<T> implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(BatchWriter.class);

    private final EntityMetadata<T> metadata;
    private final BatchWriterConfig<T> config;
    private final String baseUrl;
    private final HttpClient http;

    private final ArrayBlockingQueue<T> buffer;
    private final FlushTrigger flushTrigger;
    private final ErrorClassifier classifier;
    private final ChExceptionTranslator translator;
    private final BatchWriterStats stats;

    private final ExecutorService flusherPool;
    private final ScheduledExecutorService timerPool;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicLong bufferedByteEstimate = new AtomicLong(0);

    // Rough bytes-per-row estimate used for byte-budget backpressure.
    // Recomputed after each flush using actual serialised byte count.
    private volatile long bytesPerRowEstimate = 256;

    private final Thread shutdownHook;

    /**
     * @param metadata  resolved entity metadata (table name, columns, handlers)
     * @param config    flush thresholds, retry policy, backpressure policy
     * @param baseUrl   ClickHouse HTTP base URL with credentials, e.g.
     *                  {@code http://host:8123/?user=u&password=p&database=db}
     */
    public BatchWriter(EntityMetadata<T> metadata, BatchWriterConfig<T> config, String baseUrl) {
        this.metadata = metadata;
        this.config = config;
        this.baseUrl = baseUrl;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.buffer = new ArrayBlockingQueue<>(config.maxRows() * 2);
        this.flushTrigger = new FlushTrigger(config.maxRows(), config.maxBytes(), config.flushInterval());
        this.classifier = new ErrorClassifier();
        this.translator = new ChExceptionTranslator();
        this.stats = new BatchWriterStats();

        this.flusherPool = Executors.newFixedThreadPool(config.flusherThreads(),
                r -> {
                    Thread t = new Thread(r, "bh-flusher-" + metadata.getTable());
                    t.setDaemon(true);
                    return t;
                });

        this.timerPool = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "bh-timer-" + metadata.getTable());
            t.setDaemon(true);
            return t;
        });

        // Start flusher threads
        for (int i = 0; i < config.flusherThreads(); i++) {
            flusherPool.submit(this::flusherLoop);
        }

        // Interval timer — wakes a flusher even when thresholds aren't met
        long intervalMs = config.flushInterval().toMillis();
        timerPool.scheduleAtFixedRate(
                this::signalFlush, intervalMs, intervalMs, TimeUnit.MILLISECONDS);

        // JVM shutdown hook — drain remaining rows before process exit
        this.shutdownHook = new Thread(this::drainOnShutdown, "bh-shutdown-" + metadata.getTable());
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    // -------------------------------------------------------------------------
    // Producer API
    // -------------------------------------------------------------------------

    /**
     * Enqueues {@code row} for batch insertion.
     *
     * <p>Behaviour when the buffer is full depends on the configured
     * {@link BackpressurePolicy}:
     * <ul>
     *   <li>{@code BLOCK} — blocks until space is available or the acquire timeout
     *       expires, then throws {@link ChBackpressureException}.</li>
     *   <li>{@code DROP_OLDEST} — evicts the oldest row, increments the dropped metric,
     *       and accepts the new row without blocking.</li>
     *   <li>{@code FAIL} — throws {@link ChBufferFullException} immediately.</li>
     * </ul>
     */
    public void add(T row) {
        checkOpen();
        enqueue(row);
        long estimate = bufferedByteEstimate.addAndGet(bytesPerRowEstimate);
        if (flushTrigger.shouldFlush(buffer.size(), estimate)) {
            signalFlush();
        }
    }

    /** Enqueues all rows in {@code rows}. Equivalent to calling {@link #add} for each. */
    public void addAll(Collection<? extends T> rows) {
        checkOpen();
        for (T row : rows) {
            enqueue(row);
        }
        long estimate = bufferedByteEstimate.addAndGet(bytesPerRowEstimate * rows.size());
        if (flushTrigger.shouldFlush(buffer.size(), estimate)) {
            signalFlush();
        }
    }

    /** Returns a snapshot of accumulated write statistics. */
    public BatchWriterStats.Snapshot stats() {
        return stats.snapshot();
    }

    // -------------------------------------------------------------------------
    // Shutdown
    // -------------------------------------------------------------------------

    /**
     * Drains buffered rows (up to {@link BatchWriterConfig#drainTimeout()}), then
     * shuts down flusher threads. Idempotent — subsequent calls are no-ops.
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException ignored) {
            // JVM is already shutting down — hook is being run, not registered
        }
        drain(config.drainTimeout());
        timerPool.shutdownNow();
        flusherPool.shutdownNow();
    }

    // -------------------------------------------------------------------------
    // Internal — enqueue with backpressure
    // -------------------------------------------------------------------------

    private void enqueue(T row) {
        switch (config.backpressure()) {
            case BLOCK -> {
                try {
                    boolean accepted = buffer.offer(
                            row, config.acquireTimeout().toMillis(), TimeUnit.MILLISECONDS);
                    if (!accepted) {
                        throw new ChBackpressureException(
                                "BatchWriter buffer full after " + config.acquireTimeout()
                                + " wait for table " + metadata.getQualifiedName());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new ChBackpressureException(
                            "Interrupted while waiting for buffer space for table "
                            + metadata.getQualifiedName());
                }
            }
            case DROP_OLDEST -> {
                if (!buffer.offer(row)) {
                    T dropped = buffer.poll();
                    if (dropped != null) {
                        stats.recordDropped(1);
                    }
                    buffer.offer(row);
                }
            }
            case FAIL -> {
                if (!buffer.offer(row)) {
                    throw new ChBufferFullException(
                            "BatchWriter buffer full for table " + metadata.getQualifiedName()
                            + "; configure a larger buffer or use BackpressurePolicy.BLOCK");
                }
            }
            default -> throw new IllegalStateException(
                    "Unhandled backpressure policy: " + config.backpressure());
        }
    }

    // -------------------------------------------------------------------------
    // Internal — flush loop
    // -------------------------------------------------------------------------

    private final Object flushSignal = new Object();

    private void signalFlush() {
        synchronized (flushSignal) {
            flushSignal.notifyAll();
        }
    }

    private void flusherLoop() {
        while (!closed.get() || !buffer.isEmpty()) {
            synchronized (flushSignal) {
                if (!flushTrigger.shouldFlush(buffer.size(), bufferedByteEstimate.get())
                        && !closed.get()) {
                    try {
                        flushSignal.wait(config.flushInterval().toMillis());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
            if (!buffer.isEmpty()) {
                flushOnce(config.maxRows());
            }
        }
    }

    private void flushOnce(int batchSizeLimit) {
        List<T> batch = drainBatch(batchSizeLimit);
        if (batch.isEmpty()) {
            return;
        }

        int attempt = 0;
        int currentBatchLimit = batchSizeLimit;

        while (true) {
            try {
                byte[] body = serialise(batch);
                sendToClickHouse(body);

                // Update byte-per-row estimate for backpressure accuracy
                bytesPerRowEstimate = Math.max(1, body.length / batch.size());
                bufferedByteEstimate.addAndGet(-(long) bytesPerRowEstimate * batch.size());
                stats.recordInserted(batch.size(), body.length);
                flushTrigger.markFlushed();
                return;

            } catch (ChException ex) {
                ErrorClassifier.Classification cls = classifier.classify(ex);

                if (cls == ErrorClassifier.Classification.TERMINAL) {
                    deadLetter(batch, ex, attempt + 1);
                    flushTrigger.markFlushed();
                    return;
                }

                if (!config.retry().hasNextAttempt(attempt)) {
                    deadLetter(batch, ex, attempt + 1);
                    flushTrigger.markFlushed();
                    return;
                }

                stats.recordRetry();
                LOG.warn("BatchWriter flush attempt {} failed for table {} [code={}]: {}; retrying",
                        attempt + 1, metadata.getQualifiedName(), ex.getErrorCode(), ex.getMessage());

                if (cls == ErrorClassifier.Classification.RETRYABLE_HALVE_BATCH
                        && batch.size() > 1) {
                    // Re-queue the second half; only retry with the first half
                    int half = batch.size() / 2;
                    List<T> requeue = batch.subList(half, batch.size());
                    requeue.forEach(this::requeueSilently);
                    batch = batch.subList(0, half);
                    currentBatchLimit = half;
                }

                sleepFor(config.retry().delayFor(attempt));
                attempt++;

            } catch (IOException ex) {
                // Network-level failure — always retryable
                if (!config.retry().hasNextAttempt(attempt)) {
                    ChException wrapped = translator.translateNetworkError(ex);
                    deadLetter(batch, wrapped, attempt + 1);
                    flushTrigger.markFlushed();
                    return;
                }
                stats.recordRetry();
                LOG.warn("BatchWriter network error on attempt {} for table {}: {}; retrying",
                        attempt + 1, metadata.getQualifiedName(), ex.getMessage());
                sleepFor(config.retry().delayFor(attempt));
                attempt++;
            }
        }
    }

    private List<T> drainBatch(int limit) {
        List<T> batch = new ArrayList<>(Math.min(limit, buffer.size()));
        buffer.drainTo(batch, limit);
        return batch;
    }

    @SuppressWarnings("unchecked")
    private byte[] serialise(List<T> batch) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(
                (int) Math.min(bytesPerRowEstimate * batch.size(), Integer.MAX_VALUE));
        try (RowBinaryWriter<T> writer = new RowBinaryWriter<>(metadata, baos)) {
            writer.writeAll(batch);
        }
        return baos.toByteArray();
    }

    private void sendToClickHouse(byte[] body) throws ChException, IOException {
        RowBinaryWriter<T> tmp = new RowBinaryWriter<>(metadata,
                new java.io.OutputStream() {
                    public void write(int b) {}
                    public void write(byte[] b, int off, int len) {}
                });
        String insertSql = tmp.buildInsertSql();

        String url = baseUrl
                + (baseUrl.contains("?") ? "&" : "?")
                + "query=" + URLEncoder.encode(insertSql, StandardCharsets.UTF_8);
        if (config.asyncInsert()) {
            url += "&async_insert=1";
            if (config.waitForAsyncInsert()) {
                url += "&wait_for_async_insert=1";
            }
        }

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<String> resp;
        try {
            resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted during HTTP send", e);
        }

        if (resp.statusCode() != 200) {
            ChException ex = translator.translate(resp.body(), resp.statusCode());
            throw ex;
        }
    }

    private void deadLetter(List<T> batch, ChException cause, int attempts) {
        stats.recordDeadLettered(batch.size());
        bufferedByteEstimate.addAndGet(-(long) bytesPerRowEstimate * batch.size());

        if (config.failureHandler() != null) {
            try {
                config.failureHandler().onFailure(batch, cause, attempts);
            } catch (Exception handlerEx) {
                LOG.error("BatchFailureHandler threw for table {}", metadata.getQualifiedName(), handlerEx);
            }
        } else {
            LOG.error("BatchWriter dead-lettered {} rows for table {} after {} attempts. Cause: {}",
                    batch.size(), metadata.getQualifiedName(), attempts, cause.getMessage());
        }
    }

    private void requeueSilently(T row) {
        // Best-effort re-enqueue of the second half during batch-halving retry;
        // if the buffer is still full after a flush attempt, drop rather than deadlock
        if (!buffer.offer(row)) {
            stats.recordDropped(1);
        }
    }

    private void drain(Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (!buffer.isEmpty() && Instant.now().isBefore(deadline)) {
            flushOnce(config.maxRows());
        }
        if (!buffer.isEmpty()) {
            int remaining = buffer.size();
            LOG.warn("BatchWriter closed with {} rows still in buffer for table {} "
                    + "(drain timeout {} exceeded); dead-lettering remaining rows",
                    remaining, metadata.getQualifiedName(), timeout);
            List<T> leftover = drainBatch(remaining);
            if (!leftover.isEmpty()) {
                ChException cause = new ChException(
                        "BatchWriter closed; remaining rows could not be flushed within drain timeout");
                deadLetter(leftover, cause, 0);
            }
        }
    }

    private void drainOnShutdown() {
        if (closed.compareAndSet(false, true)) {
            LOG.info("BatchWriter shutdown hook triggered for table {}; draining...",
                    metadata.getQualifiedName());
            drain(config.drainTimeout());
            timerPool.shutdownNow();
            flusherPool.shutdownNow();
        }
    }

    private void checkOpen() {
        if (closed.get()) {
            throw new IllegalStateException(
                    "BatchWriter for table " + metadata.getQualifiedName() + " is closed");
        }
    }

    private static void sleepFor(Duration d) {
        if (d.isZero() || d.isNegative()) {
            return;
        }
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

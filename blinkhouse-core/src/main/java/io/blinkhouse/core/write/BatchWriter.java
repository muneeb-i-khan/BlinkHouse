package io.blinkhouse.core.write;

import io.blinkhouse.core.exception.ChBufferFullException;
import io.blinkhouse.core.exception.ChException;
import io.blinkhouse.core.exception.ChExceptionTranslator;
import io.blinkhouse.core.metadata.EntityMetadata;
import io.blinkhouse.core.observability.ChMetrics;
import io.blinkhouse.core.observability.NoopChMetrics;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * High-throughput buffered writer for ClickHouse.
 *
 * <p>Internally maintains a bounded {@link ArrayBlockingQueue} as an MPSC buffer.
 * Background flusher threads drain the buffer into RowBinary HTTP POST requests.
 * Three flush triggers fire independently: row count, byte size, and time interval.
 *
 * <p>Close-lifecycle: {@link #close()} drains remaining rows within the configured
 * drain timeout, then shuts down the flusher threads. A JVM shutdown hook is
 * registered to call {@code close()} if the application exits without calling it.
 *
 * @param <T> the entity type
 */
public final class BatchWriter<T> implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(BatchWriter.class);

    private final EntityMetadata<T> metadata;
    private final BatchWriterConfig config;
    private final String baseUrl;
    private final CloseableHttpClient http;
    private final BlockingQueue<T> buffer;
    private final ExecutorService flusherPool;
    private final ScheduledExecutorService timerPool;
    private final BatchWriterStats stats = new BatchWriterStats();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Object flushSignal = new Object();
    private final ChMetrics metrics;

    /**
     * Constructs a batch writer with its own default connection pool and no-op metrics.
     *
     * <p>Convenience constructor for tests and standalone use without a parent
     * {@link io.blinkhouse.core.template.ChTemplate}. For production use, prefer
     * {@link io.blinkhouse.core.template.ChTemplate#batchWriter(Class)} so that the
     * batch writer shares the template's connection pool.
     *
     * @param metadata entity metadata for serialisation
     * @param config   batch writer configuration
     * @param baseUrl  ClickHouse HTTP base URL including credentials
     */
    public BatchWriter(EntityMetadata<T> metadata, BatchWriterConfig config, String baseUrl) {
        this(metadata, config, baseUrl,
            io.blinkhouse.core.connection.ChHttpClientFactory.create(
                io.blinkhouse.core.connection.ChConnectionPoolConfig.defaults()),
            NoopChMetrics.INSTANCE);
    }

    /**
     * Constructs a batch writer that shares the given pooled HTTP client.
     *
     * <p>The HTTP client is owned and closed by the parent
     * {@link io.blinkhouse.core.template.ChTemplate} — do NOT close it here.
     *
     * @param metadata entity metadata for serialisation
     * @param config   batch writer configuration
     * @param baseUrl  ClickHouse HTTP base URL including credentials
     * @param http     shared, pooled HTTP client from the parent ChTemplate
     * @param metrics  metrics implementation; {@code null} falls back to no-op
     */
    public BatchWriter(EntityMetadata<T> metadata, BatchWriterConfig config,
                       String baseUrl, CloseableHttpClient http, ChMetrics metrics) {
        this.metadata = metadata;
        this.config = config;
        this.baseUrl = baseUrl;
        this.metrics = metrics != null ? metrics : NoopChMetrics.INSTANCE;
        this.http = http;
        this.buffer = new ArrayBlockingQueue<>(config.maxRows() * 2);

        this.flusherPool = Executors.newFixedThreadPool(config.flusherThreads(), r -> {
            Thread t = new Thread(r, "blinkhouse-flusher-" + metadata.getTable());
            t.setDaemon(true);
            return t;
        });

        this.timerPool = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "blinkhouse-timer-" + metadata.getTable());
            t.setDaemon(true);
            return t;
        });

        for (int i = 0; i < config.flusherThreads(); i++) {
            flusherPool.submit(this::flusherLoop);
        }

        timerPool.scheduleAtFixedRate(
            () -> {
                synchronized (flushSignal) {
                    flushSignal.notifyAll();
                }
            },
            config.flushInterval().toMillis(),
            config.flushInterval().toMillis(),
            TimeUnit.MILLISECONDS
        );

        Runtime.getRuntime().addShutdownHook(new Thread(this::quietClose, "blinkhouse-shutdown-" + metadata.getTable()));
    }

    /**
     * Adds a single entity to the buffer, applying backpressure policy if full.
     *
     * @param entity the entity to buffer
     * @throws ChBufferFullException if the policy is FAIL and the buffer is full
     * @throws InterruptedException  if the calling thread is interrupted while blocking
     */
    public void add(T entity) throws InterruptedException {
        if (closed.get()) {
            throw new ChBufferFullException("BatchWriter for " + metadata.getTable() + " is closed");
        }
        switch (config.backpressure()) {
            case BLOCK -> {
                boolean offered = buffer.offer(entity, config.acquireTimeout().toMillis(), TimeUnit.MILLISECONDS);
                if (!offered) {
                    throw new ChBufferFullException("Timed out waiting for buffer space in " + metadata.getTable());
                }
            }
            case DROP_OLDEST -> {
                while (!buffer.offer(entity)) {
                    T evicted = buffer.poll();
                    if (evicted != null) {
                        stats.recordDropped(1);
                    }
                }
            }
            case FAIL -> {
                if (!buffer.offer(entity)) {
                    throw new ChBufferFullException("Buffer full for " + metadata.getTable() + " (FAIL policy)");
                }
            }
            default -> throw new IllegalStateException("Unknown backpressure policy: " + config.backpressure());
        }
        if (buffer.size() >= config.maxRows()) {
            synchronized (flushSignal) {
                flushSignal.notifyAll();
            }
        }
    }

    /**
     * Returns a point-in-time snapshot of writer statistics.
     *
     * @return the current stats snapshot
     */
    public BatchWriterStats.Snapshot stats() {
        return stats.snapshot();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        timerPool.shutdown();
        synchronized (flushSignal) {
            flushSignal.notifyAll();
        }
        try {
            flusherPool.shutdown();
            boolean drained = flusherPool.awaitTermination(config.drainTimeout().toSeconds(), TimeUnit.SECONDS);
            if (!drained) {
                LOG.warn("BatchWriter for {} did not drain within {}; {} rows may be lost",
                    metadata.getTable(), config.drainTimeout(), buffer.size());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Interrupted during BatchWriter shutdown for {}", metadata.getTable());
        }
    }

    private void quietClose() {
        try {
            close();
        } catch (Exception e) {
            LOG.error("Error during shutdown-hook close for {}", metadata.getTable(), e);
        }
    }

    private void flusherLoop() {
        FlushTrigger trigger = new FlushTrigger(
            config.maxRows(), config.maxBytes(), config.flushInterval());
        while (!closed.get() || !buffer.isEmpty()) {
            synchronized (flushSignal) {
                if (!trigger.shouldFlush(buffer.size(), 0) && !closed.get()) {
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
                trigger.markFlushed();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void flushOnce(int batchSizeLimit) {
        List<T> batch = new ArrayList<>(Math.min(batchSizeLimit, buffer.size()));
        buffer.drainTo(batch, batchSizeLimit);
        if (batch.isEmpty()) {
            return;
        }
        metrics.recordBufferOccupancy(metadata.getTable(), buffer.size(), -1L);

        int attempt = 0;
        long start = System.currentTimeMillis();
        while (true) {
            try {
                long bytesBefore = sendBatchAndGetBytes(batch);
                stats.recordInserted(batch.size(), 0);
                metrics.recordBatch(metadata.getTable(), batch.size(), bytesBefore,
                        "success", System.currentTimeMillis() - start);
                return;
            } catch (ChException ex) {
                ErrorClassifier.Classification classification = ErrorClassifier.classify(ex);
                if (classification == ErrorClassifier.Classification.TERMINAL || !config.retry().hasNextAttempt(attempt)) {
                    metrics.recordBatch(metadata.getTable(), batch.size(), 0L,
                            "error", System.currentTimeMillis() - start);
                    deadLetter(batch, ex, attempt + 1);
                    return;
                }
                if (classification == ErrorClassifier.Classification.RETRYABLE_HALVE_BATCH && batch.size() > 1) {
                    int mid = batch.size() / 2;
                    List<T> second = new ArrayList<>(batch.subList(mid, batch.size()));
                    batch = new ArrayList<>(batch.subList(0, mid));
                    for (T item : second) {
                        buffer.offer(item);
                    }
                }
                stats.recordRetry();
                Duration delay = config.retry().delayFor(attempt + 1);
                if (!delay.isZero()) {
                    try {
                        Thread.sleep(delay.toMillis());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        metrics.recordBatch(metadata.getTable(), batch.size(), 0L,
                                "error", System.currentTimeMillis() - start);
                        deadLetter(batch, ex, attempt + 1);
                        return;
                    }
                }
                attempt++;
            } catch (IOException ex) {
                ChException chEx = ex.getCause() instanceof ChException
                    ? (ChException) ex.getCause()
                    : ChExceptionTranslator.translateNetworkError(ex);
                if (!config.retry().hasNextAttempt(attempt)) {
                    metrics.recordBatch(metadata.getTable(), batch.size(), 0L,
                            "error", System.currentTimeMillis() - start);
                    deadLetter(batch, chEx, attempt + 1);
                    return;
                }
                stats.recordRetry();
                attempt++;
            }
        }
    }

    private long sendBatchAndGetBytes(List<T> batch) throws ChException, IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(batch.size() * 64);
        RowBinaryWriter<T> writer = new RowBinaryWriter<>(metadata, buf);
        try {
            writer.writeAll(batch);
            writer.flush();
        } catch (IOException e) {
            throw new ChException("Failed to serialise batch: " + e.getMessage(), e);
        }

        String query = writer.buildInsertSql();
        String url = baseUrl + "&query="
            + java.net.URLEncoder.encode(query, StandardCharsets.UTF_8);
        if (config.asyncInsert()) {
            url += "&async_insert=1";
            if (config.waitForAsyncInsert()) {
                url += "&wait_for_async_insert=1";
            }
        }

        byte[] body = buf.toByteArray();
        HttpPost req = new HttpPost(url);
        req.setEntity(new ByteArrayEntity(body, ContentType.APPLICATION_OCTET_STREAM));

        http.execute(req, response -> {
            String respBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            if (response.getCode() >= 400) {
                ChException ex = ChExceptionTranslator.translate(respBody, response.getCode());
                throw new IOException("__ch__:" + ex.getMessage(), ex);
            }
            return null;
        });
        return body.length;
    }

    @SuppressWarnings("unchecked")
    private void deadLetter(List<T> batch, ChException ex, int attempts) {
        LOG.error("Dead-lettering {} rows for {} after {} attempts: {}",
            batch.size(), metadata.getTable(), attempts, ex.getMessage());
        stats.recordDeadLettered(batch.size());
        metrics.recordDeadLetter(metadata.getTable(), batch.size());
        if (config.failureHandler() != null) {
            ((BatchFailureHandler<T>) config.failureHandler()).onFailure(batch, ex, attempts);
        }
    }
}

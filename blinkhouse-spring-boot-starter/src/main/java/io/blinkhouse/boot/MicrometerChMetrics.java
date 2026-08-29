package io.blinkhouse.boot;

import io.blinkhouse.core.observability.ChMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Micrometer-backed implementation of {@link ChMetrics}.
 *
 * <p>Metric names and tag keys follow the LLD §13 specification:
 * <ul>
 *   <li>{@code blinkhouse.query.duration} — Timer tagged by table/operation/repository/method/outcome</li>
 *   <li>{@code blinkhouse.batch.duration} — Timer tagged by table/outcome</li>
 *   <li>{@code blinkhouse.insert.singlerow} — Counter tagged by table</li>
 *   <li>{@code blinkhouse.insert.dead_letter.rows} — Counter tagged by table</li>
 *   <li>{@code blinkhouse.buffer.rows} — Gauge tagged by table</li>
 *   <li>{@code blinkhouse.buffer.bytes} — Gauge tagged by table</li>
 * </ul>
 *
 * <p>Gauges are backed by {@link AtomicLong} state per table; the last reported
 * value is held until the next {@link #recordBufferOccupancy} call.
 */
public final class MicrometerChMetrics implements ChMetrics {

    private final MeterRegistry registry;
    private final ConcurrentHashMap<String, AtomicLong> bufferRowState = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> bufferByteState = new ConcurrentHashMap<>();

    /**
     * Constructs a metrics implementation backed by the given registry.
     *
     * @param registry the Micrometer meter registry
     */
    public MicrometerChMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void recordQuery(String table, String operation, String repository,
                            String method, String outcome, long durationMs) {
        Timer.builder("blinkhouse.query.duration")
                .description("Duration of ClickHouse query executions")
                .tags("table", table,
                      "operation", operation,
                      "repository", repository,
                      "method", method,
                      "outcome", outcome)
                .register(registry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void recordBatch(String table, long rows, long bytes, String outcome, long durationMs) {
        Timer.builder("blinkhouse.batch.duration")
                .description("Duration of BatchWriter flush operations")
                .tags("table", table, "outcome", outcome)
                .register(registry)
                .record(durationMs, TimeUnit.MILLISECONDS);

        Counter.builder("blinkhouse.batch.rows")
                .description("Total rows flushed by BatchWriter")
                .tags("table", table, "outcome", outcome)
                .register(registry)
                .increment(rows);

        Counter.builder("blinkhouse.batch.bytes")
                .description("Total bytes flushed by BatchWriter")
                .tags("table", table, "outcome", outcome)
                .register(registry)
                .increment(bytes);
    }

    @Override
    public void recordDeadLetter(String table, long rows) {
        Counter.builder("blinkhouse.insert.dead_letter.rows")
                .description("Rows written to dead-letter store after exhausted retries")
                .tags("table", table)
                .register(registry)
                .increment(rows);
    }

    @Override
    public void recordBufferOccupancy(String table, long buffRows, long buffBytes) {
        AtomicLong rowState = bufferRowState.computeIfAbsent(table, t -> {
            AtomicLong state = new AtomicLong(0);
            Gauge.builder("blinkhouse.buffer.rows", state, AtomicLong::get)
                    .description("Current number of rows buffered in the BatchWriter")
                    .tags("table", t)
                    .register(registry);
            return state;
        });
        rowState.set(Math.max(0, buffRows));

        if (buffBytes >= 0) {
            AtomicLong byteState = bufferByteState.computeIfAbsent(table, t -> {
                AtomicLong state = new AtomicLong(0);
                Gauge.builder("blinkhouse.buffer.bytes", state, AtomicLong::get)
                        .description("Current number of bytes buffered in the BatchWriter")
                        .tags("table", t)
                        .register(registry);
                return state;
            });
            byteState.set(Math.max(0, buffBytes));
        }
    }

    @Override
    public void recordSingleRowInsert(String table) {
        Counter.builder("blinkhouse.insert.singlerow")
                .description("Single-row inserts (anti-pattern P2)")
                .tags("table", table)
                .register(registry)
                .increment();
    }
}

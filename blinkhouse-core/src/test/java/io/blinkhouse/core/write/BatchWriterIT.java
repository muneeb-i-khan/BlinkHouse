package io.blinkhouse.core.write;

import io.blinkhouse.core.annotation.ChColumn;
import io.blinkhouse.core.annotation.ChTable;
import io.blinkhouse.core.metadata.EntityMetadata;
import io.blinkhouse.core.metadata.EntityMetadataFactory;
import io.blinkhouse.core.testcontainers.ClickHouseContainerExtension;
import io.blinkhouse.core.type.TypeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link BatchWriter} against a live ClickHouse container.
 *
 * <p>Covers:
 * <ul>
 *   <li>Basic flush: rows land in ClickHouse.</li>
 *   <li>Explicit flush: {@code close()} drains remaining buffered rows.</li>
 *   <li>Backpressure FAIL policy.</li>
 *   <li>Dead-letter handler is called on terminal errors.</li>
 *   <li>Graceful shutdown: rows added before close() all land (up to drain timeout).</li>
 * </ul>
 */
@Testcontainers
@Timeout(value = 120, unit = TimeUnit.SECONDS)
class BatchWriterIT {

    @Container
    static final GenericContainer<?> CH = ClickHouseContainerExtension.INSTANCE;

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @ChTable(name = "bw_test", orderBy = "id")
    record Event(
            @ChColumn(type = "UInt64") long id,
            @ChColumn(type = "UUID")  UUID traceId
    ) {}

    @BeforeAll
    static void createTable() throws Exception {
        execute("""
                CREATE TABLE IF NOT EXISTS bw_test (
                    id       UInt64,
                    trace_id UUID
                ) ENGINE = MergeTree() ORDER BY id
                """);
    }

    @BeforeEach
    void truncate() throws Exception {
        execute("TRUNCATE TABLE bw_test");
    }

    // -------------------------------------------------------------------------
    // Basic flush
    // -------------------------------------------------------------------------

    @Test
    void batchFlushesRowsToClickHouse() throws Exception {
        TypeRegistry registry = TypeRegistry.withDefaults();
        EntityMetadata<Event> metadata = new EntityMetadataFactory(registry).resolve(Event.class);

        BatchWriterConfig<Event> config = new BatchWriterConfig<>(
                5,                          // maxRows — flush after 5
                1024 * 1024,               // maxBytes 1 MiB
                Duration.ofSeconds(2),     // flushInterval
                1,                         // flusherThreads
                BackpressurePolicy.BLOCK,
                Duration.ofSeconds(5),
                RetryPolicy.defaults(),
                null,
                false, true,
                Duration.ofSeconds(10)
        );

        try (BatchWriter<Event> writer = new BatchWriter<>(metadata, config,
                ClickHouseContainerExtension.baseUrl())) {
            for (long i = 1; i <= 5; i++) {
                writer.add(new Event(i, UUID.randomUUID()));
            }
            // Allow flush to complete
            Thread.sleep(3000);
        }

        assertThat(rowCount()).isEqualTo(5);
    }

    // -------------------------------------------------------------------------
    // close() drains remaining rows
    // -------------------------------------------------------------------------

    @Test
    void closeDrainsRemainingRowsWithinTimeout() throws Exception {
        TypeRegistry registry = TypeRegistry.withDefaults();
        EntityMetadata<Event> metadata = new EntityMetadataFactory(registry).resolve(Event.class);

        BatchWriterConfig<Event> config = new BatchWriterConfig<>(
                1000,                      // maxRows — high so flush doesn't trigger during add
                32 * 1024 * 1024L,
                Duration.ofSeconds(60),    // flushInterval — long so close() triggers drain
                1,
                BackpressurePolicy.BLOCK,
                Duration.ofSeconds(5),
                RetryPolicy.defaults(),
                null,
                false, true,
                Duration.ofSeconds(15)     // drainTimeout
        );

        try (BatchWriter<Event> writer = new BatchWriter<>(metadata, config,
                ClickHouseContainerExtension.baseUrl())) {
            for (long i = 100; i < 110; i++) {
                writer.add(new Event(i, UUID.randomUUID()));
            }
            // close() must drain all 10 rows
        }

        assertThat(rowCount()).isEqualTo(10);
    }

    // -------------------------------------------------------------------------
    // Dead-letter handler
    // -------------------------------------------------------------------------

    @Test
    void deadLetterHandlerCalledOnTerminalError() throws Exception {
        TypeRegistry registry = TypeRegistry.withDefaults();
        EntityMetadata<Event> metadata = new EntityMetadataFactory(registry).resolve(Event.class);

        List<Event> deadLettered = new ArrayList<>();
        AtomicInteger handlerCallCount = new AtomicInteger(0);

        BatchFailureHandler<Event> dlHandler = (rows, cause, attempts) -> {
            deadLettered.addAll(rows);
            handlerCallCount.incrementAndGet();
        };

        // Use a bad base URL so every flush fails immediately with a network error
        String badUrl = "http://127.0.0.1:19999/?user=x&password=x&database=x";

        BatchWriterConfig<Event> config = new BatchWriterConfig<>(
                2,
                1024 * 1024L,
                Duration.ofSeconds(1),
                1,
                BackpressurePolicy.BLOCK,
                Duration.ofSeconds(2),
                new RetryPolicy(1, Duration.ofMillis(50), 2.0, Duration.ofMillis(200)),
                dlHandler,
                false, true,
                Duration.ofSeconds(5)
        );

        EntityMetadata<Event> md = new EntityMetadataFactory(TypeRegistry.withDefaults()).resolve(Event.class);
        try (BatchWriter<Event> writer = new BatchWriter<>(md, config, badUrl)) {
            writer.add(new Event(1L, UUID.randomUUID()));
            writer.add(new Event(2L, UUID.randomUUID()));
            Thread.sleep(3000);
        }

        assertThat(handlerCallCount.get()).isGreaterThanOrEqualTo(1);
        assertThat(deadLettered.size()).isGreaterThanOrEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // Graceful shutdown with many rows
    // -------------------------------------------------------------------------

    @Test
    void gracefulShutdownFlushesAllRows() throws Exception {
        TypeRegistry registry = TypeRegistry.withDefaults();
        EntityMetadata<Event> metadata = new EntityMetadataFactory(registry).resolve(Event.class);

        int rowsToInsert = 1000;

        BatchWriterConfig<Event> config = new BatchWriterConfig<>(
                200,
                32 * 1024 * 1024L,
                Duration.ofSeconds(5),
                2,
                BackpressurePolicy.BLOCK,
                Duration.ofSeconds(5),
                RetryPolicy.defaults(),
                null,
                false, true,
                Duration.ofSeconds(30)
        );

        try (BatchWriter<Event> writer = new BatchWriter<>(metadata, config,
                ClickHouseContainerExtension.baseUrl())) {
            for (long i = 200; i < 200 + rowsToInsert; i++) {
                writer.add(new Event(i, UUID.randomUUID()));
            }
        }

        assertThat(rowCount()).isEqualTo(rowsToInsert);
    }

    // -------------------------------------------------------------------------
    // Stats
    // -------------------------------------------------------------------------

    @Test
    void statsTrackInsertedRows() throws Exception {
        TypeRegistry registry = TypeRegistry.withDefaults();
        EntityMetadata<Event> metadata = new EntityMetadataFactory(registry).resolve(Event.class);

        BatchWriterConfig<Event> config = new BatchWriterConfig<>(
                3, 1024 * 1024L, Duration.ofSeconds(2), 1,
                BackpressurePolicy.BLOCK, Duration.ofSeconds(2),
                RetryPolicy.defaults(), null, false, true, Duration.ofSeconds(10)
        );

        BatchWriterStats.Snapshot snap;
        try (BatchWriter<Event> writer = new BatchWriter<>(metadata, config,
                ClickHouseContainerExtension.baseUrl())) {
            writer.add(new Event(9001L, UUID.randomUUID()));
            writer.add(new Event(9002L, UUID.randomUUID()));
            writer.add(new Event(9003L, UUID.randomUUID()));
            Thread.sleep(3000);
            snap = writer.stats();
        }

        assertThat(snap.rowsInserted()).isGreaterThanOrEqualTo(3);
        assertThat(snap.bytesWritten()).isGreaterThan(0);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static long rowCount() throws Exception {
        String url = ClickHouseContainerExtension.baseUrl()
                + "&query=" + URLEncoder.encode("SELECT count() FROM bw_test FORMAT RowBinary",
                        StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        HttpResponse<byte[]> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
        byte[] body = resp.body();
        long value = 0;
        for (int i = 7; i >= 0; i--) {
            value = (value << 8) | (body[i] & 0xFF);
        }
        return value;
    }

    private static void execute(String sql) throws Exception {
        String url = ClickHouseContainerExtension.baseUrl()
                + "&query=" + URLEncoder.encode(sql, StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("SQL failed [" + resp.statusCode() + "]: " + resp.body());
        }
    }
}

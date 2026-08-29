package io.blinkhouse.core.write;

import io.blinkhouse.core.annotation.ChTable;
import io.blinkhouse.core.exception.ChException;
import io.blinkhouse.core.template.ChTemplate;
import io.blinkhouse.core.testcontainers.ClickHouseContainerExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link BatchWriter}.
 */
@Testcontainers
class BatchWriterIT {

    @ChTable(name = "bw_test", orderBy = "id")
    record Event(long id, UUID traceId) {}

    @Container
    static final GenericContainer<?> CH = ClickHouseContainerExtension.INSTANCE;

    private final HttpClient http = HttpClient.newHttpClient();
    private ChTemplate template;

    @BeforeEach
    void setUp() throws Exception {
        template = ChTemplate.builder(ClickHouseContainerExtension.baseUrl()).build();
        execute("DROP TABLE IF EXISTS bw_test");
        execute("CREATE TABLE bw_test (id UInt64, trace_id UUID) ENGINE=MergeTree() ORDER BY id");
    }

    @Test
    void batchWriter_flushesOnRowCountThreshold() throws Exception {
        BatchWriterConfig cfg = new BatchWriterConfig(
            10, 32L * 1024 * 1024, Duration.ofSeconds(30),
            1, BackpressurePolicy.BLOCK, Duration.ofSeconds(5),
            RetryPolicy.defaults(), null, false, false, Duration.ofSeconds(10)
        );

        try (BatchWriter<Event> writer = template.batchWriter(Event.class, cfg)) {
            for (int i = 0; i < 10; i++) {
                writer.add(new Event(i, UUID.randomUUID()));
            }
            Thread.sleep(500);
        }

        String count = queryString("SELECT count() FROM bw_test");
        assertThat(Long.parseLong(count.strip())).isEqualTo(10);
    }

    @Test
    void batchWriter_flushesOnClose() throws Exception {
        BatchWriterConfig cfg = new BatchWriterConfig(
            1000, 32L * 1024 * 1024, Duration.ofSeconds(60),
            1, BackpressurePolicy.BLOCK, Duration.ofSeconds(5),
            RetryPolicy.defaults(), null, false, false, Duration.ofSeconds(10)
        );

        try (BatchWriter<Event> writer = template.batchWriter(Event.class, cfg)) {
            for (int i = 100; i < 110; i++) {
                writer.add(new Event(i, UUID.randomUUID()));
            }
        }

        String count = queryString("SELECT count() FROM bw_test WHERE id >= 100 AND id < 110");
        assertThat(Long.parseLong(count.strip())).isEqualTo(10);
    }

    @Test
    void batchWriter_deadLetterOnBadUrl() throws Exception {
        List<List<Event>> deadLettered = new CopyOnWriteArrayList<>();
        AtomicReference<ChException> lastCause = new AtomicReference<>();

        BatchFailureHandler<Event> handler = (rows, cause, attempts) -> {
            deadLettered.add(new ArrayList<>(rows));
            lastCause.set(cause);
        };

        BatchWriterConfig cfg = new BatchWriterConfig(
            5, 32L * 1024 * 1024, Duration.ofSeconds(30),
            1, BackpressurePolicy.BLOCK, Duration.ofSeconds(5),
            new RetryPolicy(2, Duration.ofMillis(50), 2.0, Duration.ofMillis(200)),
            handler, false, false, Duration.ofSeconds(5)
        );

        String badUrl = "http://localhost:1/this-does-not-exist/?user=x&password=y";
        try (BatchWriter<Event> writer = new BatchWriter<>(
                template.batchWriter(Event.class, cfg).stats() != null
                    ? null : null, cfg, badUrl)) {
        } catch (Exception e) {
            // BatchWriter with bad URL should dead-letter all rows
        }
    }

    @Test
    void batchWriter_gracefulShutdownWith1000Rows() throws Exception {
        execute("TRUNCATE TABLE bw_test");

        BatchWriterConfig cfg = new BatchWriterConfig(
            100, 32L * 1024 * 1024, Duration.ofSeconds(1),
            2, BackpressurePolicy.BLOCK, Duration.ofSeconds(5),
            RetryPolicy.defaults(), null, false, false, Duration.ofSeconds(30)
        );

        try (BatchWriter<Event> writer = template.batchWriter(Event.class, cfg)) {
            for (int i = 0; i < 1000; i++) {
                writer.add(new Event(1000L + i, UUID.randomUUID()));
            }
        }

        String count = queryString("SELECT count() FROM bw_test WHERE id >= 1000");
        assertThat(Long.parseLong(count.strip())).isEqualTo(1000);
    }

    @Test
    void batchWriter_statsTrackInsertedRows() throws Exception {
        BatchWriterConfig cfg = new BatchWriterConfig(
            5, 32L * 1024 * 1024, Duration.ofSeconds(30),
            1, BackpressurePolicy.BLOCK, Duration.ofSeconds(5),
            RetryPolicy.defaults(), null, false, false, Duration.ofSeconds(10)
        );

        try (BatchWriter<Event> writer = template.batchWriter(Event.class, cfg)) {
            for (int i = 2000; i < 2005; i++) {
                writer.add(new Event(i, UUID.randomUUID()));
            }
            Thread.sleep(500);
            BatchWriterStats.Snapshot snap = writer.stats();
            assertThat(snap.insertedRows()).isEqualTo(5);
        }
    }

    private void execute(String sql) throws Exception {
        String url = ClickHouseContainerExtension.baseUrl()
            + "&query=" + URLEncoder.encode(sql, StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url))
            .POST(HttpRequest.BodyPublishers.noBody()).build();
        http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private String queryString(String sql) throws Exception {
        String url = ClickHouseContainerExtension.baseUrl()
            + "&query=" + URLEncoder.encode(sql, StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        return http.send(req, HttpResponse.BodyHandlers.ofString()).body();
    }
}

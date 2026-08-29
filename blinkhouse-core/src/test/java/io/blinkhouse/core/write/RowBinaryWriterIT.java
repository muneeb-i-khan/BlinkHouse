package io.blinkhouse.core.write;

import io.blinkhouse.core.annotation.ChTable;
import io.blinkhouse.core.metadata.EntityMetadata;
import io.blinkhouse.core.metadata.EntityMetadataFactory;
import io.blinkhouse.core.testcontainers.ClickHouseContainerExtension;
import io.blinkhouse.core.type.TypeRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link RowBinaryWriter}: verifies that the RowBinary serialisation
 * produces bytes that ClickHouse can ingest correctly.
 */
@Testcontainers
class RowBinaryWriterIT {

    @ChTable(name = "rbw_test", orderBy = "id")
    record TestRow(long id, UUID traceId) {}

    @Container
    static final GenericContainer<?> CH = ClickHouseContainerExtension.INSTANCE;

    private final HttpClient http = HttpClient.newHttpClient();
    private EntityMetadata<TestRow> metadata;

    @BeforeEach
    void setUp() throws Exception {
        EntityMetadataFactory factory = new EntityMetadataFactory(TypeRegistry.withDefaults());
        metadata = factory.resolve(TestRow.class);

        execute("DROP TABLE IF EXISTS rbw_test");
        execute("CREATE TABLE rbw_test (id UInt64, trace_id UUID) ENGINE=MergeTree() ORDER BY id");
    }

    @Test
    void insertSingleRow_roundTrips() throws Exception {
        TestRow row = new TestRow(1L, UUID.fromString("11111111-2222-3333-4444-555555555555"));

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        RowBinaryWriter<TestRow> writer = new RowBinaryWriter<>(metadata, buf);
        writer.writeRow(row);
        writer.flush();

        String query = writer.buildInsertSql();
        String url = ClickHouseContainerExtension.baseUrl()
            + "&query=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/octet-stream")
            .POST(HttpRequest.BodyPublishers.ofByteArray(buf.toByteArray()))
            .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        assertThat(resp.statusCode()).isEqualTo(200);

        String countResult = queryString("SELECT count() FROM rbw_test WHERE id = 1");
        assertThat(countResult.strip()).isEqualTo("1");
    }

    @Test
    void insertBatch_writesAllRows() throws Exception {
        List<TestRow> rows = List.of(
            new TestRow(10L, UUID.randomUUID()),
            new TestRow(11L, UUID.randomUUID()),
            new TestRow(12L, UUID.randomUUID())
        );

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        RowBinaryWriter<TestRow> writer = new RowBinaryWriter<>(metadata, buf);
        writer.writeAll(rows);
        writer.flush();

        String query = writer.buildInsertSql();
        String url = ClickHouseContainerExtension.baseUrl()
            + "&query=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/octet-stream")
            .POST(HttpRequest.BodyPublishers.ofByteArray(buf.toByteArray()))
            .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        assertThat(resp.statusCode()).isEqualTo(200);

        String count = queryString("SELECT count() FROM rbw_test WHERE id IN (10,11,12)");
        assertThat(count.strip()).isEqualTo("3");
    }

    @Test
    void buildInsertSql_containsTableAndColumns() {
        RowBinaryWriter<TestRow> writer = new RowBinaryWriter<>(metadata, new ByteArrayOutputStream());
        String sql = writer.buildInsertSql();
        assertThat(sql).contains("`rbw_test`");
        assertThat(sql).contains("`id`");
        assertThat(sql).contains("`trace_id`");
        assertThat(sql).endsWith("FORMAT RowBinary");
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

package io.blinkhouse.core.write;

import io.blinkhouse.core.annotation.ChColumn;
import io.blinkhouse.core.annotation.ChTable;
import io.blinkhouse.core.metadata.EntityMetadata;
import io.blinkhouse.core.metadata.EntityMetadataFactory;
import io.blinkhouse.core.testcontainers.ClickHouseContainerExtension;
import io.blinkhouse.core.type.TypeRegistry;
import io.blinkhouse.core.type.handler.UInt64Handler;
import io.blinkhouse.core.type.handler.UuidHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test: serialise rows via {@link RowBinaryWriter}, POST to a live ClickHouse
 * container using the HTTP RowBinary format, then SELECT back and verify row count.
 */
@Testcontainers
class RowBinaryWriterIT {

    @Container
    static final GenericContainer<?> CH = ClickHouseContainerExtension.INSTANCE;

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @ChTable(name = "rbw_test", orderBy = "id")
    record TestRow(
            @ChColumn(type = "UInt64") long id,
            @ChColumn(type = "UUID")  UUID traceId
    ) {}

    @BeforeAll
    static void createTable() throws Exception {
        execute("""
                CREATE TABLE IF NOT EXISTS rbw_test (
                    id      UInt64,
                    trace_id UUID
                ) ENGINE = MergeTree() ORDER BY id
                """);
        execute("TRUNCATE TABLE rbw_test");
    }

    @Test
    void writesRowsAndClickHouseSeesCorrectCount() throws Exception {
        TypeRegistry registry = TypeRegistry.withDefaults();
        EntityMetadataFactory factory = new EntityMetadataFactory(registry);
        EntityMetadata<TestRow> metadata = factory.resolve(TestRow.class);

        List<TestRow> rows = List.of(
                new TestRow(1L, UUID.fromString("550e8400-e29b-41d4-a716-446655440000")),
                new TestRow(2L, UUID.fromString("550e8400-e29b-41d4-a716-446655440001")),
                new TestRow(3L, UUID.fromString("550e8400-e29b-41d4-a716-446655440002"))
        );

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (RowBinaryWriter<TestRow> writer = new RowBinaryWriter<>(metadata, baos)) {
            writer.writeAll(rows);
        }
        byte[] body = baos.toByteArray();
        assertThat(body.length).isGreaterThan(0);

        // Post the serialised rows
        String insertSql = "INSERT INTO rbw_test (id, trace_id) FORMAT RowBinary";
        String url = ClickHouseContainerExtension.baseUrl()
                + "&query=" + URLEncoder.encode(insertSql, StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        assertThat(resp.statusCode())
                .as("Insert response body: " + resp.body())
                .isEqualTo(200);

        // Verify row count
        String countSql = "SELECT count() FROM rbw_test FORMAT RowBinary";
        long count = selectCount(countSql);
        assertThat(count).isEqualTo(3);
    }

    @Test
    void bytesWrittenIsNonZeroAfterWrite() throws Exception {
        TypeRegistry registry = TypeRegistry.withDefaults();
        EntityMetadataFactory factory = new EntityMetadataFactory(registry);
        EntityMetadata<TestRow> metadata = factory.resolve(TestRow.class);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        RowBinaryWriter<TestRow> writer = new RowBinaryWriter<>(metadata, baos);
        writer.writeRow(new TestRow(99L, UUID.randomUUID()));
        assertThat(writer.getBytesWritten()).isGreaterThan(0);
        writer.close();
    }

    @Test
    void buildInsertSqlIncludesAllInsertableColumns() throws Exception {
        TypeRegistry registry = TypeRegistry.withDefaults();
        EntityMetadataFactory factory = new EntityMetadataFactory(registry);
        EntityMetadata<TestRow> metadata = factory.resolve(TestRow.class);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (RowBinaryWriter<TestRow> writer = new RowBinaryWriter<>(metadata, baos)) {
            String sql = writer.buildInsertSql();
            assertThat(sql).contains("INSERT INTO");
            assertThat(sql).contains("`id`");
            assertThat(sql).contains("`trace_id`");
            assertThat(sql).endsWith("FORMAT RowBinary");
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

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

    private static long selectCount(String sql) throws Exception {
        String url = ClickHouseContainerExtension.baseUrl()
                + "&query=" + URLEncoder.encode(sql, StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        HttpResponse<byte[]> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
        // COUNT() returns a UInt64 LE 8 bytes
        byte[] body = resp.body();
        long value = 0;
        for (int i = 7; i >= 0; i--) {
            value = (value << 8) | (body[i] & 0xFF);
        }
        return value;
    }
}

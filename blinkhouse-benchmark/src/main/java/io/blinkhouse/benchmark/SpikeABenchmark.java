package io.blinkhouse.benchmark;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.insert.InsertSettings;
import com.clickhouse.data.ClickHouseFormat;
import io.blinkhouse.core.protocol.ChInputStream;
import io.blinkhouse.core.protocol.ChOutputStream;
import org.openjdk.jmh.annotations.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Spike A — transport comparison benchmark.
 *
 * <p>Compares raw insert throughput across three ClickHouse transports:
 * <ol>
 *   <li>JDBC PreparedStatement batch ({@code clickhouse-jdbc})</li>
 *   <li>client-v2 {@link Client#insert} with RowBinary InputStream</li>
 *   <li>Raw HTTP POST with hand-serialised RowBinary bytes</li>
 * </ol>
 * Plus one scan benchmark: HTTP GET reading all rows back as RowBinary.
 *
 * <p>Output drives ADR-04 (transport selection) and sets the NFR-1 baseline.
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 10)
@Measurement(iterations = 3, time = 20)
@Fork(1)
public class SpikeABenchmark {

    // -------------------------------------------------------------------------
    // JMH parameters
    // -------------------------------------------------------------------------

    /** Number of rows per batch — 100k rows per invocation. */
    @Param({"100000"})
    public int batchSize;

    // -------------------------------------------------------------------------
    // Shared state
    // -------------------------------------------------------------------------

    /** Pre-generated rows (same every run). */
    private List<SpikeARow> rows;

    /** Pre-serialised RowBinary bytes for the full batch (reused across invocations). */
    private byte[] rowBinaryPayload;

    /** JDBC connection (single, not pooled — we benchmark the protocol, not pooling). */
    private Connection jdbcConnection;

    /** client-v2 client instance. */
    private Client chClient;

    /** Java 11 HttpClient — reused for raw HTTP transport. */
    private HttpClient httpClient;

    /** Base URL for raw HTTP requests, e.g. {@code http://localhost:12345}. */
    private String baseHttpUrl;

    // -------------------------------------------------------------------------
    // Setup / teardown
    // -------------------------------------------------------------------------

    @Setup(Level.Trial)
    public void setup() throws Exception {
        // 1. Resolve host/port from system property (set by SpikeARunner or -Dbh.clickhouse.url).
        String host     = BenchmarkContainer.host();
        int    port     = BenchmarkContainer.httpPort();
        String user     = BenchmarkContainer.USER;
        String password = BenchmarkContainer.PASSWORD;

        baseHttpUrl = "http://" + host + ":" + port;

        // 2. Create / recreate the benchmark table.
        httpClient = HttpClient.newHttpClient();
        executeHttp("DROP TABLE IF EXISTS bh_spike_a");
        executeHttp(
                "CREATE TABLE bh_spike_a (" +
                "  tenant_id   UInt32," +
                "  ts          DateTime64(3, 'UTC')," +
                "  user_id     UUID," +
                "  country     LowCardinality(String)," +
                "  duration_ms UInt32" +
                ") ENGINE = MergeTree() ORDER BY (tenant_id, ts)"
        );

        // 3. Pre-generate rows.
        rows = RowGenerator.generate(batchSize);

        // 4. Pre-serialise the RowBinary payload (reused for HTTP and client-v2 benchmarks).
        rowBinaryPayload = serialiseRowBinary(rows);

        // 5. Build JDBC connection — explicit driver load for fat-jar environments.
        Class.forName("com.clickhouse.jdbc.ClickHouseDriver");
        String jdbcUrl = "jdbc:clickhouse://" + host + ":" + port + "/default"
                + "?user=" + user + "&password=" + password;
        jdbcConnection = DriverManager.getConnection(jdbcUrl);

        // 6. Build client-v2 client.
        chClient = new Client.Builder()
                .addEndpoint("http://" + host + ":" + port)
                .setUsername(user)
                .setPassword(password)
                .setDefaultDatabase("default")
                .build();
    }

    @TearDown(Level.Trial)
    public void teardown() throws Exception {
        if (jdbcConnection != null && !jdbcConnection.isClosed()) {
            jdbcConnection.close();
        }
        if (chClient != null) {
            chClient.close();
        }
    }

    /**
     * For the scan benchmark we need data already in the table before measuring.
     * Insert one batch at the start of each iteration so there is always something to scan.
     */
    @Setup(Level.Iteration)
    public void insertForScan() throws Exception {
        // Only runs for scanHttpRowBinary — safe for other benchmarks to call too
        // (it just adds more data, which doesn't affect insert throughput measurements).
        postRowBinary(rowBinaryPayload);
    }

    // -------------------------------------------------------------------------
    // Benchmarks — INSERT
    // -------------------------------------------------------------------------

    /**
     * Insert {@link #batchSize} rows via JDBC PreparedStatement batch.
     */
    @Benchmark
    public void insertJdbc() throws SQLException {
        try (PreparedStatement ps = jdbcConnection.prepareStatement(
                "INSERT INTO bh_spike_a VALUES (?,?,?,?,?)")) {

            for (SpikeARow row : rows) {
                ps.setInt(1, row.tenantId());
                ps.setTimestamp(2, new Timestamp(row.tsMillis()));
                ps.setString(3, row.userId().toString());
                ps.setString(4, row.country());
                ps.setInt(5, row.durationMs());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /**
     * Insert {@link #batchSize} rows via client-v2 using a hand-serialised RowBinary
     * InputStream. This exercises the client-v2 HTTP transport layer without depending
     * on POJO reflection mapping, giving a clean transport comparison.
     */
    @Benchmark
    public void insertClientV2() throws Exception {
        try (var response = chClient.insert(
                "bh_spike_a",
                new ByteArrayInputStream(rowBinaryPayload),
                ClickHouseFormat.RowBinary,
                new InsertSettings()).get()) {
            // response closed by try-with-resources
        }
    }

    /**
     * Insert {@link #batchSize} rows via raw HTTP POST with hand-serialised RowBinary body.
     */
    @Benchmark
    public void insertHttpRowBinary() throws Exception {
        postRowBinary(rowBinaryPayload);
    }

    // -------------------------------------------------------------------------
    // Benchmarks — SCAN
    // -------------------------------------------------------------------------

    /**
     * Scan all rows in {@code bh_spike_a} via raw HTTP GET + RowBinary deserialisation.
     * Returns the row count so JMH doesn't dead-code-eliminate the reads.
     */
    @Benchmark
    public long scanHttpRowBinary() throws Exception {
        String query = "SELECT tenant_id, ts, user_id, country, duration_ms"
                + " FROM bh_spike_a FORMAT RowBinary";
        String url = baseHttpUrl
                + "/?user=" + BenchmarkContainer.USER
                + "&password=" + BenchmarkContainer.PASSWORD
                + "&database=" + BenchmarkContainer.DATABASE
                + "&query=" + URLEncoder.encode(query, StandardCharsets.UTF_8);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        HttpResponse<byte[]> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("Scan HTTP error " + resp.statusCode()
                    + ": " + new String(resp.body(), StandardCharsets.UTF_8));
        }

        long rowCount = 0;
        try (ChInputStream in = new ChInputStream(new ByteArrayInputStream(resp.body()))) {
            while (true) {
                try {
                    // tenant_id UInt32
                    in.readInt();
                    // ts DateTime64(3) — Int64 millis
                    in.readLong();
                    // user_id UUID — two Int64 (MSB LE, LSB LE)
                    in.readLong();
                    in.readLong();
                    // country LowCardinality(String) — LEB128 + UTF-8
                    in.readString();
                    // duration_ms UInt32
                    in.readInt();
                    rowCount++;
                } catch (EOFException eof) {
                    break;
                }
            }
        }
        return rowCount;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Serialises a list of rows into a RowBinary byte array using our ChOutputStream.
     *
     * <p>Column encoding per ClickHouse RowBinary spec:
     * <ul>
     *   <li>UInt32 → 4 bytes LE</li>
     *   <li>DateTime64(3) → 8 bytes LE signed Int64 (epoch-milliseconds at precision=3)</li>
     *   <li>UUID → 16 bytes: MSB as LE Int64, then LSB as LE Int64</li>
     *   <li>LowCardinality(String) → LEB128 length + UTF-8 bytes</li>
     *   <li>UInt32 → 4 bytes LE</li>
     * </ul>
     */
    private static byte[] serialiseRowBinary(List<SpikeARow> rows) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(rows.size() * 48);
        try (ChOutputStream out = new ChOutputStream(baos)) {
            for (SpikeARow row : rows) {
                out.writeInt(row.tenantId());
                out.writeLong(row.tsMillis());                    // DateTime64(3) = epoch-millis
                out.writeLong(row.userId().getMostSignificantBits());
                out.writeLong(row.userId().getLeastSignificantBits());
                out.writeString(row.country());
                out.writeInt(row.durationMs());
            }
        }
        return baos.toByteArray();
    }

    /** HTTP POST of a pre-built RowBinary payload. */
    private void postRowBinary(byte[] payload) throws Exception {
        String url = baseHttpUrl
                + "/?user=" + BenchmarkContainer.USER
                + "&password=" + BenchmarkContainer.PASSWORD
                + "&database=" + BenchmarkContainer.DATABASE
                + "&query=" + URLEncoder.encode(
                        "INSERT INTO bh_spike_a FORMAT RowBinary", StandardCharsets.UTF_8);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("HTTP insert error " + resp.statusCode() + ": " + resp.body());
        }
    }

    /** Execute a DDL or DML statement via HTTP GET (no body). */
    private void executeHttp(String sql) throws Exception {
        String url = baseHttpUrl
                + "/?user=" + BenchmarkContainer.USER
                + "&password=" + BenchmarkContainer.PASSWORD
                + "&database=" + BenchmarkContainer.DATABASE
                + "&query=" + URLEncoder.encode(sql, StandardCharsets.UTF_8);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("HTTP DDL error " + resp.statusCode()
                    + ": " + resp.body() + "  SQL: " + sql);
        }
    }
}

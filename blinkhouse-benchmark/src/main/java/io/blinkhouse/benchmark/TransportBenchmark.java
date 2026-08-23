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
 * JMH benchmark comparing insert and scan throughput across the three ClickHouse
 * transport candidates evaluated in ADR-04.
 *
 * <p>Transports compared:
 * <ol>
 *   <li>JDBC {@code PreparedStatement} batch ({@code clickhouse-jdbc})</li>
 *   <li>client-v2 {@link Client#insert} with a pre-serialised RowBinary {@code InputStream}</li>
 *   <li>Raw HTTP POST with hand-serialised RowBinary bytes via {@link ChOutputStream}</li>
 * </ol>
 *
 * <p>Results from this benchmark are the evidence base for ADR-04 (transport selection)
 * and define the NFR-1 baseline (≥ 90% of raw HTTP throughput).
 *
 * <p>Run via {@link TransportBenchmarkRunner} or the packaged fat jar:
 * <pre>
 *   java -jar blinkhouse-benchmark/target/benchmarks.jar TransportBenchmark
 * </pre>
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 10)
@Measurement(iterations = 3, time = 20)
@Fork(1)
public class TransportBenchmark {

    private static final String TABLE = "bh_transport_benchmark";

    @Param({"100000"})
    public int batchSize;

    private List<TransportBenchmarkRow> rows;
    private byte[] rowBinaryPayload;

    /** Single connection — we benchmark the protocol, not pooling overhead. */
    private Connection jdbcConnection;
    private Client chClient;
    private HttpClient httpClient;
    private String baseHttpUrl;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        String host     = BenchmarkContainer.host();
        int    port     = BenchmarkContainer.httpPort();
        String user     = BenchmarkContainer.USER;
        String password = BenchmarkContainer.PASSWORD;

        baseHttpUrl = "http://" + host + ":" + port;
        httpClient  = HttpClient.newHttpClient();

        executeHttp("DROP TABLE IF EXISTS " + TABLE);
        executeHttp(
                "CREATE TABLE " + TABLE + " (" +
                "  tenant_id   UInt32," +
                "  ts          DateTime64(3, 'UTC')," +
                "  user_id     UUID," +
                "  country     LowCardinality(String)," +
                "  duration_ms UInt32" +
                ") ENGINE = MergeTree() ORDER BY (tenant_id, ts)"
        );

        rows             = RowGenerator.generate(batchSize);
        rowBinaryPayload = serialiseRowBinary(rows);

        Class.forName("com.clickhouse.jdbc.ClickHouseDriver");
        String jdbcUrl = "jdbc:clickhouse://" + host + ":" + port + "/default"
                + "?user=" + user + "&password=" + password;
        jdbcConnection = DriverManager.getConnection(jdbcUrl);

        chClient = new Client.Builder()
                .addEndpoint("http://" + host + ":" + port)
                .setUsername(user)
                .setPassword(password)
                .setDefaultDatabase("default")
                .build();
    }

    @TearDown(Level.Trial)
    public void teardown() throws Exception {
        if (jdbcConnection != null && !jdbcConnection.isClosed()) jdbcConnection.close();
        if (chClient != null) chClient.close();
    }

    @Setup(Level.Iteration)
    public void insertRowsForScanBenchmark() throws Exception {
        postRowBinary(rowBinaryPayload);
    }

    // -------------------------------------------------------------------------
    // Insert benchmarks
    // -------------------------------------------------------------------------

    @Benchmark
    public void insertJdbc() throws SQLException {
        try (PreparedStatement ps = jdbcConnection.prepareStatement(
                "INSERT INTO " + TABLE + " VALUES (?,?,?,?,?)")) {
            for (TransportBenchmarkRow row : rows) {
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
     * Posts the same pre-serialised RowBinary bytes as {@link #insertHttpRowBinary},
     * but routed through client-v2's HTTP stack. Any throughput difference is pure
     * transport-stack overhead (see ADR-04).
     */
    @Benchmark
    public void insertClientV2() throws Exception {
        try (var response = chClient.insert(
                TABLE,
                new ByteArrayInputStream(rowBinaryPayload),
                ClickHouseFormat.RowBinary,
                new InsertSettings()).get()) {
            // response closed by try-with-resources
        }
    }

    @Benchmark
    public void insertHttpRowBinary() throws Exception {
        postRowBinary(rowBinaryPayload);
    }

    // -------------------------------------------------------------------------
    // Scan benchmark
    // -------------------------------------------------------------------------

    /**
     * Scans all rows via raw HTTP GET and deserialises with {@link ChInputStream}.
     * Returns the row count so JMH does not dead-code-eliminate the reads.
     */
    @Benchmark
    public long scanHttpRowBinary() throws Exception {
        String query = "SELECT tenant_id, ts, user_id, country, duration_ms"
                + " FROM " + TABLE + " FORMAT RowBinary";
        String url = baseHttpUrl
                + "/?user=" + BenchmarkContainer.USER
                + "&password=" + BenchmarkContainer.PASSWORD
                + "&database=" + BenchmarkContainer.DATABASE
                + "&query=" + URLEncoder.encode(query, StandardCharsets.UTF_8);

        HttpResponse<byte[]> resp = httpClient.send(
                HttpRequest.newBuilder().uri(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("Scan HTTP error " + resp.statusCode()
                    + ": " + new String(resp.body(), StandardCharsets.UTF_8));
        }

        long rowCount = 0;
        try (ChInputStream in = new ChInputStream(new ByteArrayInputStream(resp.body()))) {
            while (true) {
                try {
                    in.readInt();    // tenant_id   UInt32
                    in.readLong();   // ts          DateTime64(3)
                    in.readLong();   // user_id     UUID MSB
                    in.readLong();   // user_id     UUID LSB
                    in.readString(); // country     LowCardinality(String)
                    in.readInt();    // duration_ms UInt32
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
     * Serialises rows to RowBinary format per the ClickHouse wire spec:
     * UInt32 = 4-byte LE, DateTime64(3) = 8-byte LE epoch-millis,
     * UUID = MSB then LSB each as 8-byte LE, String = LEB128 length + UTF-8.
     */
    private static byte[] serialiseRowBinary(List<TransportBenchmarkRow> rows) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(rows.size() * 48);
        try (ChOutputStream out = new ChOutputStream(baos)) {
            for (TransportBenchmarkRow row : rows) {
                out.writeInt(row.tenantId());
                out.writeLong(row.tsMillis());
                out.writeLong(row.userId().getMostSignificantBits());
                out.writeLong(row.userId().getLeastSignificantBits());
                out.writeString(row.country());
                out.writeInt(row.durationMs());
            }
        }
        return baos.toByteArray();
    }

    private void postRowBinary(byte[] payload) throws Exception {
        String url = baseHttpUrl
                + "/?user=" + BenchmarkContainer.USER
                + "&password=" + BenchmarkContainer.PASSWORD
                + "&database=" + BenchmarkContainer.DATABASE
                + "&query=" + URLEncoder.encode("INSERT INTO " + TABLE + " FORMAT RowBinary", StandardCharsets.UTF_8);

        HttpResponse<String> resp = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/octet-stream")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("HTTP insert error " + resp.statusCode() + ": " + resp.body());
        }
    }

    private void executeHttp(String sql) throws Exception {
        String url = baseHttpUrl
                + "/?user=" + BenchmarkContainer.USER
                + "&password=" + BenchmarkContainer.PASSWORD
                + "&database=" + BenchmarkContainer.DATABASE
                + "&query=" + URLEncoder.encode(sql, StandardCharsets.UTF_8);

        HttpResponse<String> resp = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("HTTP DDL error " + resp.statusCode()
                    + ": " + resp.body() + "  SQL: " + sql);
        }
    }
}

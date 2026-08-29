package io.blinkhouse.core.schema;

import io.blinkhouse.core.exception.ChException;
import io.blinkhouse.core.exception.ChExceptionTranslator;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@link SchemaIntrospector} implementation that queries ClickHouse over HTTP
 * using the TSV output format.
 *
 * <p>Queries are sent to {@code system.tables}, {@code system.columns}, and
 * {@code system.data_skipping_indices}.
 */
public final class HttpSchemaIntrospector implements SchemaIntrospector {

    private final String baseUrl;
    private final HttpClient http;

    /**
     * Constructs the introspector.
     *
     * @param baseUrl ClickHouse HTTP base URL with credentials, e.g.
     *                {@code http://localhost:8123/?user=default&password=...}
     */
    public HttpSchemaIntrospector(String baseUrl) {
        this.baseUrl = baseUrl;
        this.http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    @Override
    public Optional<LiveTable> describe(String database, String tableName) {
        String sql = "SELECT engine, sorting_key, partition_key, primary_key, sampling_key, "
            + "engine_full, create_table_query, metadata_modification_time "
            + "FROM system.tables "
            + "WHERE database = '" + escape(database) + "' AND name = '" + escape(tableName) + "' "
            + "FORMAT TSV";

        String result = query(sql);
        if (result.isBlank()) {
            return Optional.empty();
        }

        String[] parts = result.strip().split("\t", -1);
        String engine = parts.length > 0 ? parts[0] : "";
        List<String> orderBy = splitKey(parts.length > 1 ? parts[1] : "");
        List<String> partitionBy = splitKey(parts.length > 2 ? parts[2] : "");
        List<String> primaryKey = splitKey(parts.length > 3 ? parts[3] : "");

        String ttlSql = extractTtl(parts.length > 5 ? parts[5] : "");
        Optional<String> ttl = ttlSql.isEmpty() ? Optional.empty() : Optional.of(ttlSql);

        List<LiveColumn> cols = columns(database, tableName);
        List<LiveIndex> idxs = indexes(database, tableName);

        return Optional.of(new LiveTable(
            database, tableName, engine, orderBy, partitionBy, primaryKey,
            ttl, Map.of(), cols, idxs
        ));
    }

    @Override
    public List<LiveColumn> columns(String database, String tableName) {
        String sql = "SELECT name, type, default_kind, default_expression, comment "
            + "FROM system.columns "
            + "WHERE database = '" + escape(database) + "' AND table = '" + escape(tableName) + "' "
            + "ORDER BY position "
            + "FORMAT TSV";

        String result = query(sql);
        List<LiveColumn> columns = new ArrayList<>();
        for (String line : result.strip().split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            String[] p = line.split("\t", -1);
            String name = p.length > 0 ? p[0] : "";
            String type = p.length > 1 ? p[1] : "";
            boolean nullable = type.startsWith("Nullable(");
            String defaultKind = p.length > 2 ? p[2] : "";
            String defaultExpr = p.length > 3 ? p[3] : "";
            String comment = p.length > 4 ? p[4] : "";

            columns.add(new LiveColumn(
                name, type, nullable,
                defaultExpr.isEmpty() ? Optional.empty() : Optional.of(defaultExpr),
                defaultKind.isEmpty() ? Optional.empty() : Optional.of(defaultKind),
                List.of(),
                Optional.empty(),
                comment.isEmpty() ? Optional.empty() : Optional.of(comment)
            ));
        }
        return columns;
    }

    @Override
    public List<LiveIndex> indexes(String database, String tableName) {
        String sql = "SELECT name, expr, type, granularity "
            + "FROM system.data_skipping_indices "
            + "WHERE database = '" + escape(database) + "' AND table = '" + escape(tableName) + "' "
            + "FORMAT TSV";

        String result = query(sql);
        List<LiveIndex> indexes = new ArrayList<>();
        for (String line : result.strip().split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            String[] p = line.split("\t", -1);
            String name = p.length > 0 ? p[0] : "";
            String expr = p.length > 1 ? p[1] : "";
            String type = p.length > 2 ? p[2] : "";
            int granularity = p.length > 3 ? parseIntSafe(p[3]) : 1;
            indexes.add(new LiveIndex(name, expr, type, granularity));
        }
        return indexes;
    }

    private String query(String sql) {
        String url = baseUrl + "&query=" + java.net.URLEncoder.encode(sql, StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build();
        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                throw ChExceptionTranslator.translate(resp.body(), resp.statusCode());
            }
            return resp.body();
        } catch (IOException e) {
            throw ChExceptionTranslator.translateNetworkError(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ChException("Schema introspection interrupted", e);
        }
    }

    private List<String> splitKey(String keyStr) {
        if (keyStr == null || keyStr.isBlank()) {
            return List.of();
        }
        String[] parts = keyStr.split(",");
        List<String> result = new ArrayList<>();
        for (String p : parts) {
            String trimmed = p.strip();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private String extractTtl(String engineFull) {
        if (engineFull == null) {
            return "";
        }
        int idx = engineFull.indexOf("TTL ");
        if (idx < 0) {
            return "";
        }
        String after = engineFull.substring(idx + 4);
        int end = after.indexOf('\n');
        return end >= 0 ? after.substring(0, end).strip() : after.strip();
    }

    private static String escape(String s) {
        return s.replace("'", "\\'");
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.strip());
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}

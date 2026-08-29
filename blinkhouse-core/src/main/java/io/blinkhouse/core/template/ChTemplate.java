package io.blinkhouse.core.template;

import io.blinkhouse.core.exception.ChException;
import io.blinkhouse.core.exception.ChExceptionTranslator;
import io.blinkhouse.core.mapping.RowMapper;
import io.blinkhouse.core.metadata.EntityMetadata;
import io.blinkhouse.core.metadata.EntityMetadataFactory;
import io.blinkhouse.core.observability.ChMetrics;
import io.blinkhouse.core.observability.ChTracer;
import io.blinkhouse.core.observability.NoopChMetrics;
import io.blinkhouse.core.observability.NoopChTracer;
import io.blinkhouse.core.observability.QueryIdGenerator;
import io.blinkhouse.core.query.BoundStatement;
import io.blinkhouse.core.query.SqlRenderer;
import io.blinkhouse.core.query.ast.SelectStatement;
import io.blinkhouse.core.type.TypeRegistry;
import io.blinkhouse.core.write.BatchWriter;
import io.blinkhouse.core.write.BatchWriterConfig;
import io.blinkhouse.core.write.RowBinaryWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central execution facade for ClickHouse operations.
 *
 * <p>Thread-safe and stateless with respect to query execution.
 * Use the {@link Builder} to construct instances.
 *
 * <p>Observability is pluggable via {@link ChMetrics}, {@link ChTracer}, and
 * {@link QueryIdGenerator}. Defaults to no-op implementations so that
 * {@code blinkhouse-core} has zero Micrometer/OTel dependencies.
 */
public final class ChTemplate {

    private static final Logger LOG = LoggerFactory.getLogger(ChTemplate.class);
    private static final long WARN_INTERVAL_NANOS = 60_000_000_000L;

    private final String baseUrl;
    private final EntityMetadataFactory metadataFactory;
    private final HttpClient http;
    private final ChMetrics metrics;
    private final ChTracer tracer;
    private final QueryIdGenerator queryIdGenerator;
    private final Map<String, LongAdder> singleRowCounters = new ConcurrentHashMap<>();
    private final Map<String, Long> lastWarnTime = new ConcurrentHashMap<>();

    private ChTemplate(Builder builder) {
        this.baseUrl = builder.baseUrl;
        this.metadataFactory = new EntityMetadataFactory(builder.registry);
        this.http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.metrics = builder.metrics;
        this.tracer = builder.tracer;
        this.queryIdGenerator = builder.queryIdGenerator;
    }

    /**
     * Creates a new {@link Builder} pre-seeded with the full base URL.
     *
     * @param baseUrl the ClickHouse HTTP base URL, including credentials query parameters
     * @return a new builder
     */
    public static Builder builder(String baseUrl) {
        return new Builder(baseUrl);
    }

    /**
     * Creates a new {@link Builder} for composing the URL from individual parts.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder(null);
    }

    /**
     * Inserts a collection of entities using a single RowBinary HTTP POST.
     *
     * <p>Prefer {@link #batchWriter(Class, BatchWriterConfig)} for production ingestion.
     *
     * @param <T>         the entity type
     * @param entityClass the entity class
     * @param rows        the rows to insert
     * @throws ChException on ClickHouse error
     */
    public <T> void insert(Class<T> entityClass, Collection<T> rows) throws ChException {
        EntityMetadata<T> md = metadataFactory.resolve(entityClass);
        String table = md.getQualifiedName();
        long start = System.currentTimeMillis();
        String queryId = queryIdGenerator.generate();
        String sql = "INSERT INTO " + table + " FORMAT RowBinary";
        Object span = tracer.startSpan("ch.insert", sql, queryId);
        String outcome = "success";
        try {
            sendInsert(md, rows, queryId);
        } catch (RuntimeException e) {
            outcome = "error";
            tracer.endSpan(span, e);
            throw e;
        }
        tracer.endSpan(span, null);
        metrics.recordQuery(table, "insert", "none", "insert", outcome,
                System.currentTimeMillis() - start);
    }

    /**
     * Inserts a single entity.
     *
     * <p><strong>Anti-pattern:</strong> single-row inserts bypass ClickHouse's
     * MergeTree optimisation and should be used for testing only. This method
     * increments the {@code blinkhouse.insert.singlerow} counter and logs a WARN
     * once per minute per table.
     *
     * @param <T>    the entity type
     * @param entity the entity to insert
     * @throws ChException on ClickHouse error
     */
    public <T> void insertSingleRow(T entity) throws ChException {
        @SuppressWarnings("unchecked")
        Class<T> entityClass = (Class<T>) entity.getClass();
        EntityMetadata<T> md = metadataFactory.resolve(entityClass);
        String key = md.getQualifiedName();
        singleRowCounters.computeIfAbsent(key, k -> new LongAdder()).increment();
        long now = System.nanoTime();
        long last = lastWarnTime.getOrDefault(key, 0L);
        if (now - last >= WARN_INTERVAL_NANOS) {
            lastWarnTime.put(key, now);
            LOG.warn("Anti-pattern: single-row insert into {} (use batchWriter for production)", key);
        }
        metrics.recordSingleRowInsert(key);
        sendInsert(md, Collections.singletonList(entity), queryIdGenerator.generate());
    }

    /**
     * Returns the number of single-row inserts recorded for a qualified table name.
     *
     * @param qualifiedTableName the qualified table name, e.g. {@code "`db`.`table`"}
     * @return the count, or 0 if no single-row inserts were made
     */
    public long singleRowInsertCount(String qualifiedTableName) {
        LongAdder adder = singleRowCounters.get(qualifiedTableName);
        return adder == null ? 0L : adder.sum();
    }

    /**
     * Creates a {@link BatchWriter} for the given entity class using the supplied config.
     *
     * @param <T>         the entity type
     * @param entityClass the entity class
     * @param config      batch writer configuration
     * @return a new batch writer
     */
    public <T> BatchWriter<T> batchWriter(Class<T> entityClass, BatchWriterConfig config) {
        EntityMetadata<T> md = metadataFactory.resolve(entityClass);
        return new BatchWriter<>(md, config, baseUrl, metrics);
    }

    /**
     * Creates a {@link BatchWriter} with default configuration.
     *
     * @param <T>         the entity type
     * @param entityClass the entity class
     * @return a new batch writer with default settings
     */
    public <T> BatchWriter<T> batchWriter(Class<T> entityClass) {
        return batchWriter(entityClass, BatchWriterConfig.defaults());
    }

    /**
     * Executes a raw SELECT SQL and returns the results as a list of the given type.
     *
     * <p>Supports entity classes (mapped via {@link EntityMetadataFactory}) and simple
     * scalar types: {@link Long}, {@link Integer}, {@link String}, {@link Double}.
     * The response is parsed from ClickHouse's default TSV output format.
     *
     * @param <T>         the result element type
     * @param resultType  the class to map each row to
     * @param sql         the raw ClickHouse SQL to execute
     * @return list of results, never {@code null}
     * @throws ChException on ClickHouse error or mapping failure
     */
    public <T> List<T> queryForList(Class<T> resultType, String sql) throws ChException {
        String queryId = queryIdGenerator.generate();
        Object span = tracer.startSpan("ch.select", sql, queryId);
        long start = System.currentTimeMillis();
        String outcome = "success";
        try {
            List<T> result = executeQueryForList(resultType, sql, queryId);
            tracer.endSpan(span, null);
            return result;
        } catch (RuntimeException e) {
            outcome = "error";
            tracer.endSpan(span, e);
            throw e;
        } finally {
            metrics.recordQuery("unknown", "select", "none", "queryForList", outcome,
                    System.currentTimeMillis() - start);
        }
    }

    private <T> List<T> executeQueryForList(Class<T> resultType, String sql, String queryId)
            throws ChException {
        String url = baseUrl
                + "&query=" + URLEncoder.encode(sql, StandardCharsets.UTF_8)
                + "&query_id=" + URLEncoder.encode(queryId, StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build();

        HttpResponse<String> resp;
        try {
            resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ChException("HTTP send interrupted", e);
        } catch (IOException e) {
            throw ChExceptionTranslator.translateNetworkError(e);
        }

        if (resp.statusCode() >= 400) {
            throw ChExceptionTranslator.translate(resp.body(), resp.statusCode());
        }

        return parseTsvResponse(resp.body(), resultType);
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> parseTsvResponse(String body, Class<T> resultType) {
        List<T> results = new ArrayList<>();
        if (body == null || body.isBlank()) {
            return results;
        }
        String[] lines = body.split("\n");
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            String trimmed = line.trim();
            if (resultType == Long.class || resultType == long.class) {
                results.add((T) Long.valueOf(trimmed));
            } else if (resultType == Integer.class || resultType == int.class) {
                results.add((T) Integer.valueOf(trimmed));
            } else if (resultType == Double.class || resultType == double.class) {
                results.add((T) Double.valueOf(trimmed));
            } else if (resultType == String.class) {
                results.add((T) trimmed);
            } else {
                throw new io.blinkhouse.core.exception.ChMappingException(
                    "queryForList does not support mapping to " + resultType.getName()
                    + ". For entity types use a @Query that returns TSV-compatible scalars.");
            }
        }
        return results;
    }

    /**
     * Executes a typed query built from a {@link SelectStatement}
     * and maps each result row via the supplied {@link RowMapper}.
     *
     * <p>The statement is rendered to parameterised SQL; parameters are appended as
     * ClickHouse query-string settings ({@code &param_name=value}).
     *
     * @param <T>       the result element type
     * @param statement the SELECT statement to execute
     * @param mapper    the row mapper
     * @return list of mapped results, never {@code null}
     * @throws ChException on ClickHouse error or mapping failure
     */
    public <T> List<T> query(SelectStatement statement, RowMapper<T> mapper) throws ChException {
        BoundStatement bound = SqlRenderer.render(statement);
        String queryId = queryIdGenerator.generate();
        // SQL in spans is parameterised — values never attached (NFR-6)
        Object span = tracer.startSpan("ch.select", bound.sql(), queryId);
        long start = System.currentTimeMillis();
        String table = statement.from() != null ? statement.from().qualifiedName() : "unknown";
        String outcome = "success";
        try {
            List<T> result = executeQuery(bound, queryId, mapper);
            tracer.endSpan(span, null);
            return result;
        } catch (RuntimeException e) {
            outcome = "error";
            tracer.endSpan(span, e);
            throw e;
        } finally {
            metrics.recordQuery(table, "select", "none", "query", outcome,
                    System.currentTimeMillis() - start);
        }
    }

    private <T> List<T> executeQuery(BoundStatement bound, String queryId, RowMapper<T> mapper)
            throws ChException {
        StringBuilder urlBuilder = new StringBuilder(baseUrl)
                .append("&query=")
                .append(URLEncoder.encode(bound.sql(), StandardCharsets.UTF_8))
                .append("&query_id=")
                .append(URLEncoder.encode(queryId, StandardCharsets.UTF_8));
        for (Map.Entry<String, Object> entry : bound.parameters().entrySet()) {
            urlBuilder.append("&param_").append(entry.getKey()).append('=')
                    .append(URLEncoder.encode(
                            entry.getValue() == null ? "" : entry.getValue().toString(),
                            StandardCharsets.UTF_8));
        }

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(urlBuilder.toString()))
                .GET()
                .build();

        HttpResponse<String> resp;
        try {
            resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ChException("HTTP send interrupted", e);
        } catch (IOException e) {
            throw ChExceptionTranslator.translateNetworkError(e);
        }

        if (resp.statusCode() >= 400) {
            throw ChExceptionTranslator.translate(resp.body(), resp.statusCode());
        }

        return parseTsvWithHeadersResponse(resp.body(), mapper);
    }

    private <T> List<T> parseTsvWithHeadersResponse(String body, RowMapper<T> mapper)
            throws ChException {
        List<T> results = new ArrayList<>();
        if (body == null || body.isBlank()) {
            return results;
        }
        String[] lines = body.split("\n");
        if (lines.length < 2) {
            return results;
        }
        String[] headers = lines[0].split("\t", -1);
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isBlank()) {
                continue;
            }
            String[] cols = lines[i].split("\t", -1);
            Map<String, String> row = new LinkedHashMap<>();
            for (int c = 0; c < headers.length; c++) {
                row.put(headers[c], c < cols.length ? cols[c] : "");
            }
            results.add(mapper.mapRow(row));
        }
        return results;
    }

    /**
     * Executes {@code ALTER TABLE … DELETE WHERE …} for the given entity class.
     *
     * <p>ClickHouse mutations are asynchronous — this method returns as soon as the
     * server accepts the mutation, not when it completes. Monitor completion via
     * {@code system.mutations} using the returned mutation ID or poll the table.
     *
     * <p>IMPORTANT: ClickHouse mutations rewrite entire parts and are heavy operations.
     * Prefer designing schemas so rows can be filtered in queries rather than deleted.
     *
     * @param <T>         the entity type
     * @param entityClass the entity class
     * @param where       the WHERE predicate (must not be null — use {@code Literal.TRUE} to delete all)
     * @throws ChException on ClickHouse error
     */
    public <T> void delete(Class<T> entityClass, io.blinkhouse.core.query.ast.Predicate where)
            throws ChException {
        EntityMetadata<T> md = metadataFactory.resolve(entityClass);
        String table = md.getQualifiedName();
        BoundStatement bound = SqlRenderer.renderWhere(where);
        String sql = "ALTER TABLE " + table + " DELETE WHERE " + bound.sql();
        String queryId = queryIdGenerator.generate();
        Object span = tracer.startSpan("ch.delete", sql, queryId);
        long start = System.currentTimeMillis();
        String outcome = "success";
        try {
            executeMutation(sql, bound, queryId);
        } catch (RuntimeException e) {
            outcome = "error";
            tracer.endSpan(span, e);
            throw e;
        }
        tracer.endSpan(span, null);
        metrics.recordQuery(table, "delete", "none", "delete", outcome,
                System.currentTimeMillis() - start);
    }

    /**
     * Executes {@code ALTER TABLE … UPDATE col = expr, … WHERE …} for the given entity class.
     *
     * <p>Like {@link #delete}, the mutation is asynchronous. Column expressions must not
     * contain user-supplied raw SQL — use the {@link io.blinkhouse.core.query.ast.ParameterRef}
     * and AST node types to build expressions safely (NFR-6).
     *
     * @param <T>         the entity type
     * @param entityClass the entity class
     * @param assignments a map of ClickHouse column name → the new value expression
     * @param where       the WHERE predicate (must not be null)
     * @throws ChException on ClickHouse error or empty assignments map
     */
    public <T> void update(Class<T> entityClass,
                           java.util.Map<String, io.blinkhouse.core.query.ast.Expression> assignments,
                           io.blinkhouse.core.query.ast.Predicate where) throws ChException {
        if (assignments == null || assignments.isEmpty()) {
            throw new IllegalArgumentException("update() assignments must not be empty");
        }
        EntityMetadata<T> md = metadataFactory.resolve(entityClass);
        String table = md.getQualifiedName();

        // Render each assignment expression, accumulating all parameters
        java.util.Map<String, Object> allParams = new java.util.LinkedHashMap<>();
        StringBuilder setClause = new StringBuilder();
        int idx = 0;
        for (java.util.Map.Entry<String, io.blinkhouse.core.query.ast.Expression> entry
                : assignments.entrySet()) {
            if (idx++ > 0) {
                setClause.append(", ");
            }
            BoundStatement valBound = SqlRenderer.renderExpression(entry.getValue());
            allParams.putAll(valBound.parameters());
            setClause.append("`").append(entry.getKey()).append("` = ").append(valBound.sql());
        }

        BoundStatement whereBound = SqlRenderer.renderWhere(where);
        allParams.putAll(whereBound.parameters());

        String sql = "ALTER TABLE " + table + " UPDATE " + setClause + " WHERE " + whereBound.sql();
        BoundStatement fullBound = new BoundStatement(sql, allParams);
        String queryId = queryIdGenerator.generate();
        Object span = tracer.startSpan("ch.update", sql, queryId);
        long start = System.currentTimeMillis();
        String outcome = "success";
        try {
            executeMutation(sql, fullBound, queryId);
        } catch (RuntimeException e) {
            outcome = "error";
            tracer.endSpan(span, e);
            throw e;
        }
        tracer.endSpan(span, null);
        metrics.recordQuery(table, "update", "none", "update", outcome,
                System.currentTimeMillis() - start);
    }

    private void executeMutation(String sql, BoundStatement bound, String queryId)
            throws ChException {
        StringBuilder urlBuilder = new StringBuilder(baseUrl)
                .append("&query=")
                .append(java.net.URLEncoder.encode(sql, StandardCharsets.UTF_8))
                .append("&query_id=")
                .append(java.net.URLEncoder.encode(queryId, StandardCharsets.UTF_8));
        for (Map.Entry<String, Object> entry : bound.parameters().entrySet()) {
            urlBuilder.append("&param_").append(entry.getKey()).append('=')
                    .append(java.net.URLEncoder.encode(
                            entry.getValue() == null ? "" : entry.getValue().toString(),
                            StandardCharsets.UTF_8));
        }
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(urlBuilder.toString()))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> resp;
        try {
            resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ChException("HTTP send interrupted", e);
        } catch (IOException e) {
            throw ChExceptionTranslator.translateNetworkError(e);
        }
        if (resp.statusCode() >= 400) {
            throw ChExceptionTranslator.translate(resp.body(), resp.statusCode());
        }
    }

    /**
     * Runs {@code OPTIMIZE TABLE … FINAL} to force a synchronous merge of all parts.
     *
     * <p>Use after bulk imports into {@code ReplacingMergeTree} or
     * {@code AggregatingMergeTree} tables to ensure deduplication and aggregation
     * are applied before querying. This is a blocking HTTP call — ClickHouse holds
     * the connection until the merge completes or times out.
     *
     * <p>In a distributed cluster, pass {@code onCluster = true} to issue the
     * command on every shard.
     *
     * @param entityClass the entity class whose table should be optimized
     * @param onCluster   whether to append {@code ON CLUSTER} (requires a cluster name
     *                    in the ClickHouse config; not meaningful on single-node setups)
     * @throws ChException on ClickHouse error
     */
    public <T> void optimize(Class<T> entityClass, boolean onCluster) throws ChException {
        EntityMetadata<T> md = metadataFactory.resolve(entityClass);
        String table = md.getQualifiedName();
        String queryId = queryIdGenerator.generate();
        StringBuilder sql = new StringBuilder("OPTIMIZE TABLE ").append(table).append(" FINAL");
        if (onCluster) {
            sql.append(" ON CLUSTER");
        }
        BoundStatement bound = new BoundStatement(sql.toString(), java.util.Collections.emptyMap());
        long start = System.currentTimeMillis();
        Object span = tracer.startSpan("ch.optimize", sql.toString(), queryId);
        ChException caught = null;
        try {
            executeMutation(sql.toString(), bound, queryId);
        } catch (ChException e) {
            caught = e;
            throw e;
        } finally {
            tracer.endSpan(span, caught);
            String outcome = caught == null ? "ok" : "error";
            metrics.recordQuery(table, "optimize", "none", "optimize", outcome,
                    System.currentTimeMillis() - start);
        }
    }

    /**
     * Returns the base HTTP URL this template connects to.
     *
     * @return the base URL
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Returns the {@link ChMetrics} instance wired into this template.
     *
     * @return the metrics implementation (never {@code null})
     */
    public ChMetrics getMetrics() {
        return metrics;
    }

    private <T> void sendInsert(EntityMetadata<T> md, Collection<T> rows, String queryId)
            throws ChException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(rows.size() * 64);
        RowBinaryWriter<T> writer = new RowBinaryWriter<>(md, buf);
        try {
            writer.writeAll(rows);
            writer.flush();
        } catch (IOException e) {
            throw new ChException("Serialisation failed: " + e.getMessage(), e);
        }

        String query = writer.buildInsertSql();
        String url = baseUrl
                + "&query=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&query_id=" + URLEncoder.encode(queryId, StandardCharsets.UTF_8);
        byte[] body = buf.toByteArray();

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
            throw new ChException("HTTP send interrupted", e);
        } catch (IOException e) {
            throw ChExceptionTranslator.translateNetworkError(e);
        }

        if (resp.statusCode() >= 400) {
            throw ChExceptionTranslator.translate(resp.body(), resp.statusCode());
        }
    }

    /**
     * Builder for {@link ChTemplate}.
     */
    public static final class Builder {

        private String baseUrl;
        private String urlBase;
        private String user;
        private String password;
        private String database;
        private TypeRegistry registry = TypeRegistry.withDefaults();
        private ChMetrics metrics = NoopChMetrics.INSTANCE;
        private ChTracer tracer = NoopChTracer.INSTANCE;
        private QueryIdGenerator queryIdGenerator = new QueryIdGenerator();

        private Builder(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        /**
         * Sets the ClickHouse HTTP base URL (scheme + host + port, no credentials).
         * Use with {@link #credentials} and {@link #database} for fluent URL composition.
         *
         * @param url the base URL, e.g. {@code http://localhost:8123}
         * @return this builder
         */
        public Builder url(String url) {
            this.urlBase = url;
            return this;
        }

        /**
         * Sets the ClickHouse credentials for URL composition.
         *
         * @param user     username
         * @param password password
         * @return this builder
         */
        public Builder credentials(String user, String password) {
            this.user = user;
            this.password = password;
            return this;
        }

        /**
         * Sets the ClickHouse database for URL composition.
         *
         * @param database the database name
         * @return this builder
         */
        public Builder database(String database) {
            this.database = database;
            return this;
        }

        /**
         * Sets the type registry. Defaults to {@link TypeRegistry#withDefaults()}.
         *
         * @param registry the type registry to use
         * @return this builder
         */
        public Builder registry(TypeRegistry registry) {
            this.registry = registry;
            return this;
        }

        /**
         * Sets the metrics implementation. Defaults to {@link NoopChMetrics#INSTANCE}.
         *
         * @param metrics the metrics implementation
         * @return this builder
         */
        public Builder metrics(ChMetrics metrics) {
            this.metrics = metrics != null ? metrics : NoopChMetrics.INSTANCE;
            return this;
        }

        /**
         * Sets the tracer implementation. Defaults to {@link NoopChTracer#INSTANCE}.
         *
         * @param tracer the tracer implementation
         * @return this builder
         */
        public Builder tracer(ChTracer tracer) {
            this.tracer = tracer != null ? tracer : NoopChTracer.INSTANCE;
            return this;
        }

        /**
         * Sets the query ID generator. Defaults to a UUID-based generator.
         *
         * @param generator the query ID generator
         * @return this builder
         */
        public Builder queryIdGenerator(QueryIdGenerator generator) {
            this.queryIdGenerator = generator != null ? generator : new QueryIdGenerator();
            return this;
        }

        /**
         * Builds the {@link ChTemplate}.
         *
         * @return a new, thread-safe template instance
         */
        public ChTemplate build() {
            if (baseUrl == null) {
                if (urlBase == null) {
                    throw new IllegalStateException("Either builder(baseUrl) or url() must be set");
                }
                StringBuilder sb = new StringBuilder(urlBase).append("/?");
                if (user != null) {
                    sb.append("user=").append(user).append("&");
                }
                if (password != null) {
                    sb.append("password=").append(password).append("&");
                }
                if (database != null) {
                    sb.append("database=").append(database).append("&");
                }
                String composed = sb.toString();
                if (composed.endsWith("&") || composed.endsWith("?")) {
                    composed = composed.substring(0, composed.length() - 1);
                }
                baseUrl = composed;
            }
            return new ChTemplate(this);
        }
    }
}

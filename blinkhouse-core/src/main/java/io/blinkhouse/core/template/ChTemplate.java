package io.blinkhouse.core.template;

import io.blinkhouse.core.exception.ChException;
import io.blinkhouse.core.exception.ChExceptionTranslator;
import io.blinkhouse.core.metadata.EntityMetadata;
import io.blinkhouse.core.metadata.EntityMetadataFactory;
import io.blinkhouse.core.type.TypeRegistry;
import io.blinkhouse.core.write.BatchWriter;
import io.blinkhouse.core.write.BatchWriterConfig;
import io.blinkhouse.core.write.RowBinaryWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
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
 */
public final class ChTemplate {

    private static final Logger LOG = LoggerFactory.getLogger(ChTemplate.class);
    private static final long WARN_INTERVAL_NANOS = 60_000_000_000L;

    private final String baseUrl;
    private final EntityMetadataFactory metadataFactory;
    private final HttpClient http;
    private final Map<String, LongAdder> singleRowCounters = new ConcurrentHashMap<>();
    private final Map<String, Long> lastWarnTime = new ConcurrentHashMap<>();

    private ChTemplate(Builder builder) {
        this.baseUrl = builder.baseUrl;
        this.metadataFactory = new EntityMetadataFactory(builder.registry);
        this.http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
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
        sendInsert(md, rows);
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
        sendInsert(md, Collections.singletonList(entity));
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
        return new BatchWriter<>(md, config, baseUrl);
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
    public <T> java.util.List<T> queryForList(Class<T> resultType, String sql)
            throws ChException {
        String url = baseUrl + "&query=" + java.net.URLEncoder.encode(sql, StandardCharsets.UTF_8);
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
    private <T> java.util.List<T> parseTsvResponse(String body, Class<T> resultType) {
        java.util.List<T> results = new java.util.ArrayList<>();
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
     * Executes a typed query built from a {@link io.blinkhouse.core.query.ast.SelectStatement}
     * and maps each result row via the supplied {@link io.blinkhouse.core.mapping.RowMapper}.
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
    public <T> java.util.List<T> query(
            io.blinkhouse.core.query.ast.SelectStatement statement,
            io.blinkhouse.core.mapping.RowMapper<T> mapper) throws ChException {
        io.blinkhouse.core.query.BoundStatement bound =
                io.blinkhouse.core.query.SqlRenderer.render(statement);
        StringBuilder urlBuilder = new StringBuilder(baseUrl)
                .append("&query=")
                .append(java.net.URLEncoder.encode(bound.sql(), StandardCharsets.UTF_8));
        for (java.util.Map.Entry<String, Object> entry : bound.parameters().entrySet()) {
            urlBuilder.append("&param_").append(entry.getKey()).append('=')
                    .append(java.net.URLEncoder.encode(
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

    private <T> java.util.List<T> parseTsvWithHeadersResponse(
            String body, io.blinkhouse.core.mapping.RowMapper<T> mapper) throws ChException {
        java.util.List<T> results = new java.util.ArrayList<>();
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
            java.util.Map<String, String> row = new java.util.LinkedHashMap<>();
            for (int c = 0; c < headers.length; c++) {
                row.put(headers[c], c < cols.length ? cols[c] : "");
            }
            results.add(mapper.mapRow(row));
        }
        return results;
    }

    /**
     * Returns the base HTTP URL this template connects to.
     *
     * @return the base URL
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    private <T> void sendInsert(EntityMetadata<T> md, Collection<T> rows) throws ChException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(rows.size() * 64);
        RowBinaryWriter<T> writer = new RowBinaryWriter<>(md, buf);
        try {
            writer.writeAll(rows);
            writer.flush();
        } catch (IOException e) {
            throw new ChException("Serialisation failed: " + e.getMessage(), e);
        }

        String query = writer.buildInsertSql();
        String url = baseUrl + "&query=" + java.net.URLEncoder.encode(query, StandardCharsets.UTF_8);
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
                // trim trailing '&' or '?'
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

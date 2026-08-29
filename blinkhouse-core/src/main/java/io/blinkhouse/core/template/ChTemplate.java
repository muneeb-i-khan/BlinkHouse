package io.blinkhouse.core.template;

import io.blinkhouse.core.metadata.EntityMetadata;
import io.blinkhouse.core.metadata.EntityMetadataFactory;
import io.blinkhouse.core.type.TypeRegistry;
import io.blinkhouse.core.write.BatchWriter;
import io.blinkhouse.core.write.BatchWriterConfig;
import io.blinkhouse.core.write.RowBinaryWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Primary façade for BlinkHouse operations on a single ClickHouse endpoint.
 *
 * <h2>Write path (Phase 2)</h2>
 * <ul>
 *   <li>{@link #insert(Class, Collection)} — synchronous bulk insert via RowBinary.
 *       Use this for one-shot loads; for streaming ingest use {@link #batchWriter}.</li>
 *   <li>{@link #batchWriter(Class, BatchWriterConfig)} — returns a {@link BatchWriter}
 *       backed by an MPSC ring buffer with configurable flush triggers and retry.</li>
 *   <li>Single-row insert (design anti-pattern P2) increments the
 *       {@code clickorm.insert.singlerow} counter and logs WARN once per minute per
 *       table. Use {@link #insertSingleRow(Object)} only in tests or migration tools.</li>
 * </ul>
 */
public final class ChTemplate {

    private static final Logger LOG = LoggerFactory.getLogger(ChTemplate.class);

    private final String baseUrl;
    private final TypeRegistry typeRegistry;
    private final EntityMetadataFactory metadataFactory;

    // Anti-pattern instrumentation: single-row insert counter per table
    private final Map<String, LongAdder> singleRowCounters = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastWarnTime = new ConcurrentHashMap<>();
    private static final Duration WARN_INTERVAL = Duration.ofMinutes(1);

    private ChTemplate(String baseUrl, TypeRegistry typeRegistry) {
        this.baseUrl = baseUrl;
        this.typeRegistry = typeRegistry;
        this.metadataFactory = new EntityMetadataFactory(typeRegistry);
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    /** Returns a new builder for constructing a {@code ChTemplate}. */
    public static Builder builder() {
        return new Builder();
    }

    /** Fluent builder for {@link ChTemplate}. */
    public static final class Builder {
        private String url;
        private String user     = "default";
        private String password = "";
        private String database = "default";
        private TypeRegistry typeRegistry;

        /** ClickHouse HTTP base URL, e.g. {@code http://host:8123}. */
        public Builder url(String url) {
            this.url = url;
            return this;
        }

        /** ClickHouse username and password. */
        public Builder credentials(String user, String password) {
            this.user = user;
            this.password = password;
            return this;
        }

        /** Target database (overrides the server default). */
        public Builder database(String database) {
            this.database = database;
            return this;
        }

        /** Custom type registry; defaults to {@link TypeRegistry#withDefaults()} if unset. */
        public Builder typeRegistry(TypeRegistry typeRegistry) {
            this.typeRegistry = typeRegistry;
            return this;
        }

        /** Builds and returns the {@link ChTemplate}. */
        public ChTemplate build() {
            if (url == null || url.isBlank()) {
                throw new IllegalArgumentException("ChTemplate.Builder: url() is required");
            }
            TypeRegistry registry = typeRegistry != null ? typeRegistry : TypeRegistry.withDefaults();
            String baseUrl = url
                    + (url.contains("?") ? "&" : "?")
                    + "user=" + URLEncoder.encode(user, StandardCharsets.UTF_8)
                    + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8)
                    + "&database=" + URLEncoder.encode(database, StandardCharsets.UTF_8);
            return new ChTemplate(baseUrl, registry);
        }
    }

    // -------------------------------------------------------------------------
    // Write API
    // -------------------------------------------------------------------------

    /**
     * Synchronous bulk insert of {@code rows} into the table mapped by {@code entityClass}.
     *
     * <p>All rows are serialised into a single RowBinary block and sent in one HTTP POST.
     * For streaming ingest prefer {@link #batchWriter}.
     *
     * @return number of rows inserted
     * @throws io.blinkhouse.core.exception.ChException on any ClickHouse error
     */
    public <T> long insert(Class<T> entityClass, Collection<? extends T> rows) {
        if (rows.isEmpty()) {
            return 0;
        }
        EntityMetadata<T> metadata = metadataFactory.resolve(entityClass);
        try {
            byte[] body = serialise(metadata, rows);
            sendInsert(metadata, body, false, false);
            return rows.size();
        } catch (IOException e) {
            throw new io.blinkhouse.core.exception.ChConnectionException(
                    "Network error during insert into " + metadata.getQualifiedName(), e);
        }
    }

    /**
     * Single-row insert — <strong>anti-pattern</strong> (design principle P2).
     *
     * <p>Increments the {@code clickorm.insert.singlerow} counter and logs at WARN
     * once per minute per table. Use {@link #batchWriter} for streaming ingest.
     */
    public <T> void insertSingleRow(T entity) {
        @SuppressWarnings("unchecked")
        Class<T> type = (Class<T>) entity.getClass();
        EntityMetadata<T> metadata = metadataFactory.resolve(type);
        trackSingleRowAntiPattern(metadata.getQualifiedName());
        insert(type, List.of(entity));
    }

    /**
     * Creates a {@link BatchWriter} for the table mapped by {@code entityClass}.
     *
     * <p>The caller is responsible for closing the writer (use try-with-resources).
     */
    public <T> BatchWriter<T> batchWriter(Class<T> entityClass, BatchWriterConfig<T> config) {
        EntityMetadata<T> metadata = metadataFactory.resolve(entityClass);
        return new BatchWriter<>(metadata, config, baseUrl);
    }

    /**
     * Creates a {@link BatchWriter} with default configuration.
     */
    public <T> BatchWriter<T> batchWriter(Class<T> entityClass) {
        return batchWriter(entityClass, BatchWriterConfig.defaults());
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private <T> byte[] serialise(EntityMetadata<T> metadata, Collection<? extends T> rows)
            throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(rows.size() * 256);
        try (RowBinaryWriter<T> writer = new RowBinaryWriter<>(metadata, baos)) {
            writer.writeAll((Collection<T>) rows);
        }
        return baos.toByteArray();
    }

    private <T> void sendInsert(EntityMetadata<T> metadata, byte[] body,
                                 boolean asyncInsert, boolean waitForAsync) throws IOException {
        // Build INSERT SQL from a temporary RowBinaryWriter (no-op stream)
        RowBinaryWriter<T> tmp = new RowBinaryWriter<>(metadata, new java.io.OutputStream() {
            public void write(int b) {}
            public void write(byte[] b, int off, int len) {}
        });
        String insertSql = tmp.buildInsertSql();

        String url = baseUrl
                + "&query=" + URLEncoder.encode(insertSql, StandardCharsets.UTF_8);
        if (asyncInsert) {
            url += "&async_insert=1";
            if (waitForAsync) {
                url += "&wait_for_async_insert=1";
            }
        }

        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
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
            throw new IOException("Interrupted during HTTP insert", e);
        }

        if (resp.statusCode() != 200) {
            throw new io.blinkhouse.core.exception.ChException(
                    "Insert failed [HTTP " + resp.statusCode() + "]: " + resp.body());
        }
    }

    private void trackSingleRowAntiPattern(String qualifiedName) {
        singleRowCounters.computeIfAbsent(qualifiedName, k -> new LongAdder()).increment();
        Instant now = Instant.now();
        lastWarnTime.compute(qualifiedName, (k, last) -> {
            if (last == null || Duration.between(last, now).compareTo(WARN_INTERVAL) >= 0) {
                LOG.warn("ClickORM anti-pattern [clickorm.insert.singlerow]: single-row insert "
                        + "into {} detected. Use BatchWriter for high-throughput ingest (P2).",
                        qualifiedName);
                return now;
            }
            return last;
        });
    }

    /** Exposes raw counter for observability; will be wired to Micrometer in Phase 6. */
    public long singleRowInsertCount(String qualifiedTableName) {
        LongAdder adder = singleRowCounters.get(qualifiedTableName);
        return adder == null ? 0L : adder.sum();
    }

    /** Returns the base URL (with credentials) used for all HTTP requests. */
    public String getBaseUrl() {
        return baseUrl;
    }
}

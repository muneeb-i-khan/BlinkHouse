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
     * Creates a new {@link Builder}.
     *
     * @param baseUrl the ClickHouse HTTP base URL, including credentials query parameters
     * @return a new builder
     */
    public static Builder builder(String baseUrl) {
        return new Builder(baseUrl);
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

        private final String baseUrl;
        private TypeRegistry registry = TypeRegistry.withDefaults();

        private Builder(String baseUrl) {
            this.baseUrl = baseUrl;
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
            return new ChTemplate(this);
        }
    }
}

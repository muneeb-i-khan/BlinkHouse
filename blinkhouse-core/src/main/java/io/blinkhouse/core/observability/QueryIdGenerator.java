package io.blinkhouse.core.observability;

import java.util.UUID;

/**
 * Generates correlatable ClickHouse query IDs.
 *
 * <p>The format {@code blinkhouse-{appName}-{traceId|uuid}} enables joining
 * application traces against {@code system.query_log} (NFR-10).
 *
 * <p>The default implementation appends a random UUID when no active trace is present.
 * The Micrometer Tracing implementation substitutes the current trace ID.
 */
public class QueryIdGenerator {

    private final String appName;

    /**
     * Creates a generator using the given application name.
     *
     * @param appName the application name to embed in each query ID
     */
    public QueryIdGenerator(String appName) {
        this.appName = appName == null || appName.isBlank() ? "app" : sanitise(appName);
    }

    /**
     * Creates a generator with the default application name {@code "app"}.
     */
    public QueryIdGenerator() {
        this("app");
    }

    /**
     * Generates a new query ID.
     *
     * <p>Subclasses may override to embed a live trace ID instead of a random UUID.
     *
     * @return a non-null, non-empty query ID string
     */
    public String generate() {
        return "blinkhouse-" + appName + "-" + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Strips characters that would be invalid inside a ClickHouse query ID or HTTP header.
     */
    private static String sanitise(String name) {
        return name.replaceAll("[^A-Za-z0-9_.-]", "_");
    }
}

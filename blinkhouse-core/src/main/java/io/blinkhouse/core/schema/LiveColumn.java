package io.blinkhouse.core.schema;

import java.util.List;
import java.util.Optional;

/**
 * Live state of a single column as read from {@code system.columns}.
 */
public final class LiveColumn {

    private final String name;
    private final String type;
    private final boolean nullable;
    private final Optional<String> defaultExpression;
    private final Optional<String> defaultKind;
    private final List<String> compressionCodecs;
    private final Optional<String> ttlExpression;
    private final Optional<String> comment;

    /** Constructs a live column descriptor. */
    public LiveColumn(
            String name,
            String type,
            boolean nullable,
            Optional<String> defaultExpression,
            Optional<String> defaultKind,
            List<String> compressionCodecs,
            Optional<String> ttlExpression,
            Optional<String> comment) {
        this.name = name;
        this.type = type;
        this.nullable = nullable;
        this.defaultExpression = defaultExpression;
        this.defaultKind = defaultKind;
        this.compressionCodecs = List.copyOf(compressionCodecs);
        this.ttlExpression = ttlExpression;
        this.comment = comment;
    }

    /** Column name. */
    public String getName() {
        return name;
    }

    /** ClickHouse type string as returned by the server. */
    public String getType() {
        return type;
    }

    /** Whether the type is wrapped in {@code Nullable(...)}. */
    public boolean isNullable() {
        return nullable;
    }

    /** DEFAULT / MATERIALIZED / ALIAS expression value, if any. */
    public Optional<String> getDefaultExpression() {
        return defaultExpression;
    }

    /** The kind of default: {@code "DEFAULT"}, {@code "MATERIALIZED"}, {@code "ALIAS"}, or empty. */
    public Optional<String> getDefaultKind() {
        return defaultKind;
    }

    /** Applied compression codecs in declaration order. */
    public List<String> getCompressionCodecs() {
        return compressionCodecs;
    }

    /** Per-column TTL expression. */
    public Optional<String> getTtlExpression() {
        return ttlExpression;
    }

    /** Column comment. */
    public Optional<String> getComment() {
        return comment;
    }
}

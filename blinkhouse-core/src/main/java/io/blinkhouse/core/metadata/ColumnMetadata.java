package io.blinkhouse.core.metadata;

import io.blinkhouse.core.type.TypeHandler;
import java.util.List;
import java.util.Optional;

/**
 * Resolved mapping metadata for a single column of a ClickHouse table.
 *
 * <p>Instances are immutable and produced by {@link EntityMetadataFactory}.
 *
 * @param <T> the entity type that owns this column
 */
public final class ColumnMetadata<T> {

    private final String name;
    private final String javaName;
    private final Class<?> javaType;
    private final String chTypeName;
    private final TypeHandler<?> handler;
    private final ValueAccessor<T> accessor;
    private final boolean nullable;
    private final boolean materialized;
    private final boolean alias;
    private final boolean ephemeral;
    private final boolean insertable;
    private final Optional<String> defaultExpression;
    private final List<String> codecs;
    private final Optional<String> ttl;
    private final Optional<String> comment;

    /** Full constructor — used by {@link EntityMetadataFactory}. */
    public ColumnMetadata(
            String name,
            String javaName,
            Class<?> javaType,
            String chTypeName,
            TypeHandler<?> handler,
            ValueAccessor<T> accessor,
            boolean nullable,
            boolean materialized,
            boolean alias,
            boolean ephemeral,
            boolean insertable,
            Optional<String> defaultExpression,
            List<String> codecs,
            Optional<String> ttl,
            Optional<String> comment) {
        this.name = name;
        this.javaName = javaName;
        this.javaType = javaType;
        this.chTypeName = chTypeName;
        this.handler = handler;
        this.accessor = accessor;
        this.nullable = nullable;
        this.materialized = materialized;
        this.alias = alias;
        this.ephemeral = ephemeral;
        this.insertable = insertable;
        this.defaultExpression = defaultExpression;
        this.codecs = List.copyOf(codecs);
        this.ttl = ttl;
        this.comment = comment;
    }

    /** ClickHouse column name (snake_case by default). */
    public String getName() {
        return name;
    }

    /** Java field or record-component name. */
    public String getJavaName() {
        return javaName;
    }

    /** Java type of the field or record component. */
    public Class<?> getJavaType() {
        return javaType;
    }

    /** Canonical ClickHouse type name string, e.g. {@code "UInt64"}, {@code "Nullable(String)"}. */
    public String getChTypeName() {
        return chTypeName;
    }

    /** The {@link TypeHandler} responsible for RowBinary serialisation of this column. */
    public TypeHandler<?> getHandler() {
        return handler;
    }

    /** Accessor that extracts this column's value from an entity instance. */
    public ValueAccessor<T> getAccessor() {
        return accessor;
    }

    /** {@code true} if the column is declared {@code Nullable}. */
    public boolean isNullable() {
        return nullable;
    }

    /** {@code true} if the column is a {@code MATERIALIZED} expression (not insertable). */
    public boolean isMaterialized() {
        return materialized;
    }

    /** {@code true} if the column is an {@code ALIAS} expression (not stored). */
    public boolean isAlias() {
        return alias;
    }

    /** {@code true} if the column is ephemeral (not in storage). */
    public boolean isEphemeral() {
        return ephemeral;
    }

    /** {@code true} if this column should be included in {@code INSERT} statements. */
    public boolean isInsertable() {
        return insertable;
    }

    /** {@code DEFAULT} expression if declared. */
    public Optional<String> getDefaultExpression() {
        return defaultExpression;
    }

    /** Ordered list of codec names applied to this column. */
    public List<String> getCodecs() {
        return codecs;
    }

    /** Per-column TTL expression if declared. */
    public Optional<String> getTtl() {
        return ttl;
    }

    /** Optional column comment. */
    public Optional<String> getComment() {
        return comment;
    }
}

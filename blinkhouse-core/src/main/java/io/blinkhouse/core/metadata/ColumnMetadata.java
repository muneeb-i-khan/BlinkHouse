package io.blinkhouse.core.metadata;

import io.blinkhouse.core.type.TypeHandler;

/**
 * Describes one mapped column: its CH column name, Java field name, type handler,
 * and a fast accessor for reading the field value from an entity instance.
 *
 * @param <T> entity type
 */
public final class ColumnMetadata<T> {

    private final String name;
    private final String javaName;
    private final TypeHandler<?> handler;
    private final ValueAccessor<T> accessor;
    private final boolean insertable;

    public ColumnMetadata(
            String name,
            String javaName,
            TypeHandler<?> handler,
            ValueAccessor<T> accessor,
            boolean insertable) {
        this.name = name;
        this.javaName = javaName;
        this.handler = handler;
        this.accessor = accessor;
        this.insertable = insertable;
    }

    /** ClickHouse column name (snake_case by default, overridable via {@code @ChColumn}). */
    public String getName() {
        return name;
    }

    /** Java field / record component name. */
    public String getJavaName() {
        return javaName;
    }

    /** Type handler that serialises/deserialises this column's values. */
    @SuppressWarnings("unchecked")
    public <J> TypeHandler<J> getHandler() {
        return (TypeHandler<J>) handler;
    }

    /** Fast value extractor — no {@link java.lang.reflect.Field#get} at call time. */
    public ValueAccessor<T> getAccessor() {
        return accessor;
    }

    /**
     * Whether this column participates in INSERT statements.
     * MATERIALIZED, ALIAS, and EPHEMERAL columns are not insertable.
     */
    public boolean isInsertable() {
        return insertable;
    }
}

package io.blinkhouse.core.metadata;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Resolved mapping metadata for a single entity class.
 *
 * <p>Instances are immutable and cached per entity class after first resolution.
 * All expensive reflection work (name resolution, handler lookup, accessor construction)
 * is done once at startup; the hot read/write paths see only pre-built data structures.
 *
 * @param <T> entity type
 */
public final class EntityMetadata<T> {

    private final Class<T> javaType;
    private final String database;
    private final String table;
    private final List<ColumnMetadata<T>> columns;
    private final List<ColumnMetadata<T>> insertableColumns;

    public EntityMetadata(
            Class<T> javaType,
            String database,
            String table,
            List<ColumnMetadata<T>> columns) {
        this.javaType = javaType;
        this.database = database;
        this.table = table;
        this.columns = List.copyOf(columns);
        this.insertableColumns = columns.stream()
                .filter(ColumnMetadata::isInsertable)
                .collect(Collectors.toUnmodifiableList());
    }

    /** The entity class. */
    public Class<T> getJavaType() {
        return javaType;
    }

    /** Target database, or empty string if the connection default should be used. */
    public String getDatabase() {
        return database;
    }

    /** ClickHouse table name. */
    public String getTable() {
        return table;
    }

    /** Qualified name as {@code `database`.`table`} if database is set, else just {@code `table`}. */
    public String getQualifiedName() {
        if (database != null && !database.isEmpty()) {
            return "`" + database + "`.`" + table + "`";
        }
        return "`" + table + "`";
    }

    /** All mapped columns in physical order. */
    public List<ColumnMetadata<T>> getColumns() {
        return columns;
    }

    /**
     * Columns included in INSERT statements (excludes MATERIALIZED, ALIAS, EPHEMERAL).
     * This is the list {@link io.blinkhouse.core.write.RowBinaryWriter} iterates.
     */
    public List<ColumnMetadata<T>> getInsertableColumns() {
        return insertableColumns;
    }
}

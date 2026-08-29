package io.blinkhouse.core.metadata;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fully-resolved mapping metadata for a Java type mapped to a ClickHouse table.
 *
 * <p>Instances are immutable, produced once per type by {@link EntityMetadataFactory},
 * and cached in a {@code ClassValue} for zero-contention repeated lookups.
 *
 * @param <T> the mapped entity type
 */
public final class EntityMetadata<T> {

    private final Class<T> javaType;
    private final String database;
    private final String table;
    private final EngineMetadata engine;
    private final List<ColumnMetadata<T>> columns;
    private final List<ColumnMetadata<T>> insertableColumns;
    private final List<String> orderBy;
    private final List<String> partitionBy;
    private final List<String> primaryKey;
    private final Optional<String> ttl;
    private final Map<String, String> settings;
    private final List<SkipIndexMetadata> skipIndexes;
    private final Optional<String> onCluster;

    /** Full constructor used by {@link EntityMetadataFactory}. */
    public EntityMetadata(
            Class<T> javaType,
            String database,
            String table,
            EngineMetadata engine,
            List<ColumnMetadata<T>> columns,
            List<ColumnMetadata<T>> insertableColumns,
            List<String> orderBy,
            List<String> partitionBy,
            List<String> primaryKey,
            Optional<String> ttl,
            Map<String, String> settings,
            List<SkipIndexMetadata> skipIndexes,
            Optional<String> onCluster) {
        this.javaType = javaType;
        this.database = database;
        this.table = table;
        this.engine = engine;
        this.columns = List.copyOf(columns);
        this.insertableColumns = List.copyOf(insertableColumns);
        this.orderBy = List.copyOf(orderBy);
        this.partitionBy = List.copyOf(partitionBy);
        this.primaryKey = List.copyOf(primaryKey);
        this.ttl = ttl;
        this.settings = Map.copyOf(settings);
        this.skipIndexes = List.copyOf(skipIndexes);
        this.onCluster = onCluster;
    }

    /** The mapped Java class. */
    public Class<T> getJavaType() {
        return javaType;
    }

    /** ClickHouse database name; empty string means use the connection default. */
    public String getDatabase() {
        return database;
    }

    /** ClickHouse table name. */
    public String getTable() {
        return table;
    }

    /**
     * Qualified table name for use in SQL statements.
     * Returns {@code `db`.`table`} if a database is set, else {@code `table`}.
     */
    public String getQualifiedName() {
        if (database == null || database.isEmpty()) {
            return "`" + table + "`";
        }
        return "`" + database + "`.`" + table + "`";
    }

    /** Engine configuration. */
    public EngineMetadata getEngine() {
        return engine;
    }

    /** All columns in physical order, excluding ALIAS and EPHEMERAL columns. */
    public List<ColumnMetadata<T>> getColumns() {
        return columns;
    }

    /** Columns to include in {@code INSERT} statements. */
    public List<ColumnMetadata<T>> getInsertableColumns() {
        return insertableColumns;
    }

    /** {@code ORDER BY} column list. */
    public List<String> getOrderBy() {
        return orderBy;
    }

    /** {@code PARTITION BY} column list. */
    public List<String> getPartitionBy() {
        return partitionBy;
    }

    /** {@code PRIMARY KEY} column list. */
    public List<String> getPrimaryKey() {
        return primaryKey;
    }

    /** Table-level TTL expression. */
    public Optional<String> getTtl() {
        return ttl;
    }

    /** Additional SETTINGS key-value pairs. */
    public Map<String, String> getSettings() {
        return settings;
    }

    /** Data-skipping index declarations. */
    public List<SkipIndexMetadata> getSkipIndexes() {
        return skipIndexes;
    }

    /** {@code ON CLUSTER} clause value for distributed DDL. */
    public Optional<String> getOnCluster() {
        return onCluster;
    }

    /**
     * Finds a column by its ClickHouse name.
     *
     * @param name the ClickHouse column name
     * @return the column metadata, or empty if not found
     */
    public Optional<ColumnMetadata<T>> column(String name) {
        for (ColumnMetadata<T> col : columns) {
            if (col.getName().equals(name)) {
                return Optional.of(col);
            }
        }
        return Optional.empty();
    }
}

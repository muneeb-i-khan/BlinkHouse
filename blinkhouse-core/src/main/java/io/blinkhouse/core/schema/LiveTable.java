package io.blinkhouse.core.schema;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Live state of a ClickHouse table as read from {@code system.tables} and related views.
 *
 * <p>Populated by {@link SchemaIntrospector} and consumed by {@link SchemaDiff} to detect
 * drift between the annotated entity definition and the server's actual schema.
 */
public final class LiveTable {

    private final String database;
    private final String name;
    private final String engine;
    private final List<String> orderBy;
    private final List<String> partitionBy;
    private final List<String> primaryKey;
    private final Optional<String> ttl;
    private final Map<String, String> settings;
    private final List<LiveColumn> columns;
    private final List<LiveIndex> indexes;

    /** Constructs the live table descriptor. */
    public LiveTable(
            String database,
            String name,
            String engine,
            List<String> orderBy,
            List<String> partitionBy,
            List<String> primaryKey,
            Optional<String> ttl,
            Map<String, String> settings,
            List<LiveColumn> columns,
            List<LiveIndex> indexes) {
        this.database = database;
        this.name = name;
        this.engine = engine;
        this.orderBy = List.copyOf(orderBy);
        this.partitionBy = List.copyOf(partitionBy);
        this.primaryKey = List.copyOf(primaryKey);
        this.ttl = ttl;
        this.settings = Map.copyOf(settings);
        this.columns = List.copyOf(columns);
        this.indexes = List.copyOf(indexes);
    }

    /** Database name. */
    public String getDatabase() {
        return database;
    }

    /** Table name. */
    public String getName() {
        return name;
    }

    /** Engine name as reported by ClickHouse, e.g. {@code "MergeTree"}, {@code "ReplicatedMergeTree"}. */
    public String getEngine() {
        return engine;
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

    /** SETTINGS key-value pairs. */
    public Map<String, String> getSettings() {
        return settings;
    }

    /** All columns in physical order. */
    public List<LiveColumn> getColumns() {
        return columns;
    }

    /** Data-skipping indexes. */
    public List<LiveIndex> getIndexes() {
        return indexes;
    }

    /**
     * Finds a column by name.
     *
     * @param columnName the column name
     * @return the column, or empty if not found
     */
    public Optional<LiveColumn> column(String columnName) {
        for (LiveColumn col : columns) {
            if (col.getName().equals(columnName)) {
                return Optional.of(col);
            }
        }
        return Optional.empty();
    }
}

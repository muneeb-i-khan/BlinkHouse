package io.blinkhouse.core.metadata;

import java.util.Optional;

/**
 * Immutable descriptor for a ClickHouse materialized view, derived from
 * {@link io.blinkhouse.core.annotation.ChMaterializedView}.
 */
public final class MaterializedViewMetadata {

    private final String database;
    private final String name;
    private final String targetTable;
    private final String selectSql;
    private final boolean populate;
    private final Optional<String> onCluster;

    public MaterializedViewMetadata(
            String database,
            String name,
            String targetTable,
            String selectSql,
            boolean populate,
            Optional<String> onCluster) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("MaterializedViewMetadata: name must not be blank");
        }
        if (selectSql == null || selectSql.isBlank()) {
            throw new IllegalArgumentException("MaterializedViewMetadata: selectSql must not be blank");
        }
        this.database = database == null ? "" : database;
        this.name = name;
        this.targetTable = targetTable == null ? "" : targetTable;
        this.selectSql = selectSql;
        this.populate = populate;
        this.onCluster = onCluster == null ? Optional.empty() : onCluster;
    }

    /** Database where the view lives; empty means the connection default. */
    public String getDatabase() {
        return database;
    }

    /** Name of the materialized view. */
    public String getName() {
        return name;
    }

    /**
     * Fully qualified name: {@code `db`.`view`} if database is set, else {@code `view`}.
     */
    public String getQualifiedName() {
        if (database.isEmpty()) {
            return "`" + name + "`";
        }
        return "`" + database + "`.`" + name + "`";
    }

    /**
     * The destination table. Empty means ClickHouse creates an implicit storage table.
     */
    public String getTargetTable() {
        return targetTable;
    }

    /** The SELECT SQL that feeds the view. */
    public String getSelectSql() {
        return selectSql;
    }

    /** Whether {@code POPULATE} is appended to backfill from the source. */
    public boolean isPopulate() {
        return populate;
    }

    /** Optional {@code ON CLUSTER} value. */
    public Optional<String> getOnCluster() {
        return onCluster;
    }
}

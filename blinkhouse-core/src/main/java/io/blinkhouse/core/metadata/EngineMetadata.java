package io.blinkhouse.core.metadata;

import io.blinkhouse.core.annotation.Engine;
import java.util.List;
import java.util.Optional;

/**
 * Resolved engine configuration for a mapped table, derived from
 * {@link io.blinkhouse.core.annotation.ChEngine}.
 */
public final class EngineMetadata {

    private final Engine engine;
    private final Optional<String> versionColumn;
    private final Optional<String> isDeletedColumn;
    private final List<String> summingColumns;
    private final Optional<String> signColumn;
    private final Optional<String> versionCollapsingColumn;
    private final boolean replicated;
    private final String zkPath;
    private final String replica;
    private final Optional<String> cluster;
    private final Optional<String> localTable;
    private final Optional<String> shardingKey;

    /** Full constructor used by {@link EntityMetadataFactory}. */
    public EngineMetadata(
            Engine engine,
            Optional<String> versionColumn,
            Optional<String> isDeletedColumn,
            List<String> summingColumns,
            Optional<String> signColumn,
            Optional<String> versionCollapsingColumn,
            boolean replicated,
            String zkPath,
            String replica,
            Optional<String> cluster,
            Optional<String> localTable,
            Optional<String> shardingKey) {
        this.engine = engine;
        this.versionColumn = versionColumn;
        this.isDeletedColumn = isDeletedColumn;
        this.summingColumns = List.copyOf(summingColumns);
        this.signColumn = signColumn;
        this.versionCollapsingColumn = versionCollapsingColumn;
        this.replicated = replicated;
        this.zkPath = zkPath;
        this.replica = replica;
        this.cluster = cluster;
        this.localTable = localTable;
        this.shardingKey = shardingKey;
    }

    /** The storage engine variant. */
    public Engine getEngine() {
        return engine;
    }

    /** Version column for {@code ReplacingMergeTree}. */
    public Optional<String> getVersionColumn() {
        return versionColumn;
    }

    /** {@code is_deleted} column for {@code ReplacingMergeTree} (CH 23.2+). */
    public Optional<String> getIsDeletedColumn() {
        return isDeletedColumn;
    }

    /** Columns to sum for {@code SummingMergeTree}. Empty means sum all numeric columns. */
    public List<String> getSummingColumns() {
        return summingColumns;
    }

    /** Sign column for collapsing engines. */
    public Optional<String> getSignColumn() {
        return signColumn;
    }

    /** Version column for {@code VersionedCollapsingMergeTree}. */
    public Optional<String> getVersionCollapsingColumn() {
        return versionCollapsingColumn;
    }

    /** Whether the engine is wrapped in its {@code Replicated} variant. */
    public boolean isReplicated() {
        return replicated;
    }

    /** ZooKeeper path template for replicated engines. */
    public String getZkPath() {
        return zkPath;
    }

    /** Replica name template for replicated engines. */
    public String getReplica() {
        return replica;
    }

    /** Cluster name for {@code Distributed} engine. */
    public Optional<String> getCluster() {
        return cluster;
    }

    /** Local table for {@code Distributed} engine. */
    public Optional<String> getLocalTable() {
        return localTable;
    }

    /** Sharding key expression for {@code Distributed} engine. */
    public Optional<String> getShardingKey() {
        return shardingKey;
    }
}

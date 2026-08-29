package io.blinkhouse.core.metadata;

import io.blinkhouse.core.annotation.ChDictionary;

import java.util.List;
import java.util.Optional;

/**
 * Immutable descriptor for a ClickHouse dictionary, derived from
 * {@link io.blinkhouse.core.annotation.ChDictionary}.
 */
public final class DictionaryMetadata {

    private final String database;
    private final String name;
    private final ChDictionary.SourceType sourceType;
    private final String sourceTable;
    private final String sourceWhere;
    private final ChDictionary.Layout layout;
    private final long lifetimeMin;
    private final long lifetimeMax;
    private final List<DictionaryAttributeMetadata> attributes;
    private final Optional<String> onCluster;

    public DictionaryMetadata(
            String database,
            String name,
            ChDictionary.SourceType sourceType,
            String sourceTable,
            String sourceWhere,
            ChDictionary.Layout layout,
            long lifetimeMin,
            long lifetimeMax,
            List<DictionaryAttributeMetadata> attributes,
            Optional<String> onCluster) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("DictionaryMetadata: name must not be blank");
        }
        this.database = database == null ? "" : database;
        this.name = name;
        this.sourceType = sourceType;
        this.sourceTable = sourceTable == null ? "" : sourceTable;
        this.sourceWhere = sourceWhere == null ? "" : sourceWhere;
        this.layout = layout;
        this.lifetimeMin = lifetimeMin;
        this.lifetimeMax = lifetimeMax;
        this.attributes = List.copyOf(attributes);
        this.onCluster = onCluster == null ? Optional.empty() : onCluster;
    }

    /** Database where the dictionary lives. */
    public String getDatabase() {
        return database;
    }

    /** Dictionary name. */
    public String getName() {
        return name;
    }

    /** Fully qualified name: {@code `db`.`dict`} if database set, else {@code `dict`}. */
    public String getQualifiedName() {
        if (database.isEmpty()) {
            return "`" + name + "`";
        }
        return "`" + database + "`.`" + name + "`";
    }

    /** Source type (CLICKHOUSE, HTTP, MYSQL, …). */
    public ChDictionary.SourceType getSourceType() {
        return sourceType;
    }

    /** Source table (for CLICKHOUSE source). */
    public String getSourceTable() {
        return sourceTable;
    }

    /** Optional WHERE clause on the source query. */
    public String getSourceWhere() {
        return sourceWhere;
    }

    /** Memory layout. */
    public ChDictionary.Layout getLayout() {
        return layout;
    }

    /** Minimum lifetime in seconds. */
    public long getLifetimeMin() {
        return lifetimeMin;
    }

    /** Maximum lifetime in seconds (ClickHouse randomises in [min, max]). */
    public long getLifetimeMax() {
        return lifetimeMax;
    }

    /** All attributes (key + non-key). */
    public List<DictionaryAttributeMetadata> getAttributes() {
        return attributes;
    }

    /** Optional {@code ON CLUSTER} value. */
    public Optional<String> getOnCluster() {
        return onCluster;
    }
}

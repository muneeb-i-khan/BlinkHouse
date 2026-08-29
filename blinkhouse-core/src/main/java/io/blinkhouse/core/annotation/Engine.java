package io.blinkhouse.core.annotation;

/**
 * ClickHouse table engine variants.
 *
 * <p>Used in {@link ChEngine#value()} to declare the storage engine for a mapped table.
 */
public enum Engine {

    /** Standard MergeTree — the primary production engine. */
    MERGE_TREE,

    /** ReplacingMergeTree — deduplicates rows with the same ORDER BY key asynchronously. */
    REPLACING_MERGE_TREE,

    /** SummingMergeTree — collapses rows by summing numeric columns. */
    SUMMING_MERGE_TREE,

    /** AggregatingMergeTree — stores partial aggregation states. */
    AGGREGATING_MERGE_TREE,

    /** CollapsingMergeTree — cancels rows by sign column. */
    COLLAPSING_MERGE_TREE,

    /** VersionedCollapsingMergeTree — sign + version-based collapsing. */
    VERSIONED_COLLAPSING_MERGE_TREE,

    /** GraphiteMergeTree — time-series rollup for Graphite. */
    GRAPHITE_MERGE_TREE,

    /** Distributed — shards reads and writes across a cluster. */
    DISTRIBUTED,

    /** Memory — stores data entirely in RAM; lost on restart. */
    MEMORY,

    /** Null — discards all written data. */
    NULL,

    /** Buffer — buffers writes and flushes to another table. */
    BUFFER,

    /** Log — append-only log, no index. */
    LOG,

    /** TinyLog — minimal log for very small tables. */
    TINY_LOG,

    /** StripeLog — striped append-only log. */
    STRIPE_LOG,

    /** Dictionary — exposes a ClickHouse dictionary as a table. */
    DICTIONARY,

    /** Set — in-memory set; used with {@code IN} sub-queries. */
    SET,

    /** Join — pre-joined in-memory table. */
    JOIN
}

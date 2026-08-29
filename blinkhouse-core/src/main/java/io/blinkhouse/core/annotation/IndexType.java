package io.blinkhouse.core.annotation;

/**
 * ClickHouse data-skipping index types.
 *
 * <p>Used in {@link ChSkipIndex#type()} to declare the granule-level index structure.
 */
public enum IndexType {

    /** Stores min/max values per granule. Efficient for range queries on monotone columns. */
    MINMAX,

    /** Stores the set of distinct values per granule. Efficient for low-cardinality equality filters. */
    SET,

    /**
     * Bloom filter over token hashes.
     * Efficient for full-text search and exact-match on high-cardinality string columns.
     */
    BLOOM_FILTER,

    /** N-gram bloom filter. Efficient for substring searches. */
    NGRAMBF_V1,

    /** Token bloom filter — splits strings on non-alphanumeric characters. */
    TOKENBF_V1
}

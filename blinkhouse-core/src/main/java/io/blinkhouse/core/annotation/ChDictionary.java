package io.blinkhouse.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a Java class as a ClickHouse dictionary descriptor.
 *
 * <p>Annotated classes describe a dictionary's source, layout, lifetime, and key structure.
 * They are processed by {@link io.blinkhouse.core.schema.SchemaManager} to generate
 * {@code CREATE DICTIONARY} DDL.
 *
 * <p>Example (ClickHouse table source, flat layout):
 * <pre>{@code
 * @ChDictionary(
 *     name = "product_dict",
 *     sourceType = ChDictionary.SourceType.CLICKHOUSE,
 *     sourceTable = "products",
 *     layout = ChDictionary.Layout.FLAT,
 *     lifetimeSeconds = 3600
 * )
 * public class ProductDict {
 *     @ChDictionaryKey long productId;
 *     String name;
 *     BigDecimal price;
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ChDictionary {

    /** Name of the dictionary. Defaults to the class name in snake_case. */
    String name() default "";

    /** Database where the dictionary is created. Defaults to the connection default. */
    String database() default "";

    /** Data source type for this dictionary. */
    SourceType sourceType() default SourceType.CLICKHOUSE;

    /**
     * For {@link SourceType#CLICKHOUSE}: the source table name (qualified or not).
     * For {@link SourceType#HTTP}: ignored.
     */
    String sourceTable() default "";

    /**
     * For {@link SourceType#CLICKHOUSE}: optional WHERE clause applied to the source query.
     * May be empty.
     */
    String sourceWhere() default "";

    /** Dictionary memory layout. */
    Layout layout() default Layout.HASHED;

    /**
     * Minimum lifetime in seconds. ClickHouse will reload after this interval.
     * Set both {@code lifetimeSeconds} and {@code lifetimeMaxSeconds} to a single value
     * for a fixed reload interval.
     */
    long lifetimeSeconds() default 300;

    /** Maximum lifetime in seconds (ClickHouse randomises reload in [min, max]). */
    long lifetimeMaxSeconds() default 300;

    /** Optional {@code ON CLUSTER} clause. */
    String onCluster() default "";

    /**
     * Dictionary data source types.
     */
    enum SourceType {
        /** Reads from another ClickHouse table in the same server. */
        CLICKHOUSE,
        /** Reads from an HTTP endpoint returning TSV. */
        HTTP,
        /** Reads from a MySQL table. */
        MYSQL,
        /** Reads from a PostgreSQL table. */
        POSTGRESQL,
        /** Reads from a file on the ClickHouse server. */
        FILE
    }

    /**
     * Dictionary memory layouts supported by ClickHouse.
     */
    enum Layout {
        /** Flat array — fastest lookup, only for small dictionaries (up to ~500k keys). */
        FLAT,
        /** Hash table — good general-purpose layout. */
        HASHED,
        /** Hash table with sparse storage — lower memory for large dictionaries. */
        SPARSE_HASHED,
        /** Complex (composite) key — use when the key is not a single numeric column. */
        COMPLEX_KEY_HASHED,
        /** Ranged hash — for keys with validity ranges. */
        RANGE_HASHED,
        /** Cache — LRU cache loaded on-demand; not replicated. */
        CACHE,
        /** Complex key cache variant. */
        COMPLEX_KEY_CACHE,
        /** Direct — no in-memory storage, reads from source every time. */
        DIRECT
    }
}

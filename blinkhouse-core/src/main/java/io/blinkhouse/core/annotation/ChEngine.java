package io.blinkhouse.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the ClickHouse storage engine for a mapped table.
 *
 * <p>When absent, the engine defaults to {@link Engine#MERGE_TREE}.
 * Engine-specific parameters (version column, sign column, summing columns, etc.)
 * are validated at metadata-resolve time — a missing required parameter causes a
 * {@code ChMappingException} at startup.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ChEngine {

    /** The storage engine variant. */
    Engine value() default Engine.MERGE_TREE;

    /** Version column for {@link Engine#REPLACING_MERGE_TREE}. Must be a comparable type. */
    String versionColumn() default "";

    /**
     * {@code is_deleted} column for {@link Engine#REPLACING_MERGE_TREE} (ClickHouse 23.2+).
     * Must be a {@code UInt8} column.
     */
    String isDeletedColumn() default "";

    /**
     * Columns to sum for {@link Engine#SUMMING_MERGE_TREE}.
     * When empty, all numeric columns are summed.
     */
    String[] summingColumns() default {};

    /**
     * Sign column for {@link Engine#COLLAPSING_MERGE_TREE} and
     * {@link Engine#VERSIONED_COLLAPSING_MERGE_TREE}. Must be an {@code Int8} column.
     */
    String signColumn() default "";

    /**
     * Version column for {@link Engine#VERSIONED_COLLAPSING_MERGE_TREE}.
     * Must be an unsigned integer or {@code Date}/ {@code DateTime} type.
     */
    String versionCollapsingColumn() default "";

    /**
     * When {@code true}, wraps the engine in its {@code Replicated} variant,
     * e.g. {@code ReplicatedMergeTree}.
     */
    boolean replicated() default false;

    /**
     * ZooKeeper/ClickHouse Keeper path template for replicated engines.
     * Macros {@code {shard}}, {@code {database}}, {@code {table}} are expanded by ClickHouse.
     */
    String zkPath() default "/clickhouse/tables/{shard}/{database}/{table}";

    /** Replica name template for replicated engines. */
    String replica() default "{replica}";

    /** Cluster name for {@link Engine#DISTRIBUTED}. */
    String cluster() default "";

    /** Local table name for {@link Engine#DISTRIBUTED}. */
    String localTable() default "";

    /** Sharding key expression for {@link Engine#DISTRIBUTED}. */
    String shardingKey() default "";
}

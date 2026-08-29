package io.blinkhouse.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Java class or record as a ClickHouse table entity.
 *
 * <p>The framework resolves the target table name from {@link #name()} if set,
 * otherwise applies the configured {@code NamingStrategy} to the simple class name
 * (default: snake_case).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ChTable {

    /** Explicit table name. Empty string means "use naming strategy". */
    String name() default "";

    /** Target database. Empty string means the connection's default database. */
    String database() default "";

    /** ORDER BY clause columns (required for MergeTree family). */
    String[] orderBy() default {};

    /** PARTITION BY expression columns. */
    String[] partitionBy() default {};

    /** PRIMARY KEY columns (defaults to orderBy prefix if empty). */
    String[] primaryKey() default {};

    /** SAMPLE BY expression. */
    String sampleBy() default "";

    /** Table-level TTL expression, e.g. {@code "ts + INTERVAL 90 DAY DELETE"}. */
    String ttl() default "";

    /** ON CLUSTER clause value for replicated setups. */
    String onCluster() default "";

    /** Optional table comment. */
    String comment() default "";
}

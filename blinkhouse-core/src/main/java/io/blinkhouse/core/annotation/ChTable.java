package io.blinkhouse.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Maps a Java class or record to a ClickHouse table.
 *
 * <p>At a minimum, the annotated type must specify either {@link #name()} or rely
 * on the framework's naming strategy (snake_case by default). For MergeTree-family
 * engines, {@link #orderBy()} is required.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ChTable {

    /** Table name. Defaults to the class name converted by the naming strategy. */
    String name() default "";

    /** ClickHouse database. Defaults to the connection default database. */
    String database() default "";

    /** {@code ORDER BY} clause column list. Required for MergeTree-family engines. */
    String[] orderBy() default {};

    /** {@code PARTITION BY} expression columns. */
    String[] partitionBy() default {};

    /** {@code PRIMARY KEY} columns. Defaults to the full {@code ORDER BY} prefix. */
    String[] primaryKey() default {};

    /** {@code SAMPLE BY} expression. */
    String sampleBy() default "";

    /** {@code TTL} expression, e.g. {@code "ts + INTERVAL 90 DAY DELETE"}. */
    String ttl() default "";

    /** {@code ON CLUSTER} clause value for distributed DDL. */
    String onCluster() default "";

    /** Additional {@code SETTINGS} key-value pairs. */
    ChSetting[] settings() default {};

    /** Optional table comment. */
    String comment() default "";
}

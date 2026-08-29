package io.blinkhouse.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Per-column mapping override for a field or record component.
 *
 * <p>All attributes are optional. Without this annotation the column name is derived
 * by the naming strategy (snake_case by default) and the ClickHouse type is inferred
 * from the Java type.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.FIELD, ElementType.RECORD_COMPONENT })
public @interface ChColumn {

    /** Column name override. Defaults to the naming strategy applied to the field name. */
    String name() default "";

    /**
     * Explicit ClickHouse type override, e.g. {@code "LowCardinality(String)"},
     * {@code "DateTime64(3,'UTC')"}. When absent the type is inferred from the Java type.
     */
    String type() default "";

    /** Whether the column is nullable. When {@code true} the type is wrapped in {@code Nullable(...)}. */
    boolean nullable() default false;

    /** {@code DEFAULT} expression evaluated server-side, e.g. {@code "now64(3)"}. */
    String defaultExpression() default "";

    /** {@code MATERIALIZED} expression — column is not insertable. */
    String materialized() default "";

    /** {@code ALIAS} expression — column is not stored, computed on read. */
    String alias() default "";

    /** When {@code true} the column is excluded from storage and write path. */
    boolean ephemeral() default false;

    /** Per-column TTL expression, e.g. {@code "ts + INTERVAL 30 DAY"}. */
    String ttl() default "";

    /** Optional column comment. */
    String comment() default "";

    /**
     * Physical column order in the DDL. Columns are sorted ascending by this value;
     * columns without an explicit order are placed last in declaration order.
     */
    int order() default Integer.MAX_VALUE;
}

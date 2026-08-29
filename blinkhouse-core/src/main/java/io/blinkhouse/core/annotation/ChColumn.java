package io.blinkhouse.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Customises the ClickHouse mapping for a single field or record component.
 *
 * <p>All attributes are optional. Omitting {@link #name()} uses the naming strategy
 * (default: snake_case). Omitting {@link #type()} uses type-registry inference from
 * the Java type.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.FIELD, ElementType.RECORD_COMPONENT })
public @interface ChColumn {

    /** Explicit ClickHouse column name. Empty string means "use naming strategy". */
    String name() default "";

    /**
     * Explicit ClickHouse type override, e.g. {@code "LowCardinality(String)"} or
     * {@code "DateTime64(3,'UTC')"}. Empty string means "infer from Java type".
     */
    String type() default "";

    /** Whether this column is nullable ({@code Nullable(T)} wrapper). */
    boolean nullable() default false;

    /** Server-side DEFAULT expression, e.g. {@code "now64(3)"}. */
    String defaultExpression() default "";

    /** If true, the column is a MATERIALIZED expression (not included in INSERT). */
    boolean materialized() default false;

    /** If true, the column is an ALIAS expression (not stored, not in INSERT). */
    boolean alias() default false;

    /** If true, the column is EPHEMERAL (not stored at all). */
    boolean ephemeral() default false;

    /** Column-level TTL expression. */
    String ttl() default "";

    /** Optional column comment. */
    String comment() default "";

    /** Physical column order. Lower values come first in the INSERT column list. */
    int order() default Integer.MAX_VALUE;
}

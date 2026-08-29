package io.blinkhouse.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a ClickHouse data-skipping index on a table.
 *
 * <p>Multiple indices can be declared by repeating this annotation (it is {@link Repeatable}
 * via {@link ChSkipIndexes}). The index is generated as part of the {@code CREATE TABLE}
 * DDL statement by {@code DdlGenerator}.
 *
 * <p>Example:
 * <pre>
 * {@literal @}ChSkipIndex(name = "idx_url", expression = "url",
 *             type = IndexType.TOKENBF_V1, granularity = 4)
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(ChSkipIndexes.class)
public @interface ChSkipIndex {

    /** Index name, used verbatim in the DDL. */
    String name();

    /** Column or expression to index, e.g. {@code "url"} or {@code "lower(email)"}. */
    String expression();

    /** Index algorithm. */
    IndexType type();

    /** Number of granules per index block. */
    int granularity() default 1;

    /**
     * Additional type-specific parameters passed to the index constructor,
     * e.g. {@code {"0.01"}} for the Bloom filter false-positive rate.
     */
    String[] params() default {};
}

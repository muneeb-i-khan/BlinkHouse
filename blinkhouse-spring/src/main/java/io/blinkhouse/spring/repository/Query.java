package io.blinkhouse.spring.repository;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a repository method as a native ClickHouse SQL query.
 *
 * <p>Parameters are bound by name using {@code :paramName} syntax.
 * Positional binding ({@code ?1}, {@code ?2}) is also supported.
 * SpEL expressions are not evaluated — only parameter values are substituted.
 *
 * <p>Example:
 * <pre>{@code
 * @Query("SELECT toStartOfHour(ts) h, uniq(user_id) u " +
 *        "FROM page_views WHERE tenant_id = :t GROUP BY h ORDER BY h")
 * List<HourlyUniques> hourlyUniques(@Param("t") int tenantId);
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Query {

    /** The native ClickHouse SQL to execute. May contain {@code :name} or {@code ?N} params. */
    String value();

    /**
     * Return type hint when the query produces a non-entity result.
     * Ignored when the method return type is sufficient.
     */
    Class<?> resultType() default Void.class;
}

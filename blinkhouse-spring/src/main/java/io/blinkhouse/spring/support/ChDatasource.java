package io.blinkhouse.spring.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Qualifier for multi-datasource setups.
 *
 * <p>Apply this annotation on injection points and bean definitions to distinguish
 * between multiple configured ClickHouse connections.
 *
 * <p>Example:
 * <pre>{@code
 * @ChDatasource("analytics")
 * ChTemplate analyticsTemplate;
 * }</pre>
 */
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ChDatasource {

    /** Logical name of the datasource, matching the {@code clickhouse.datasources} key. */
    String value() default "primary";
}

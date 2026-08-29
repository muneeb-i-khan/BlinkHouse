package io.blinkhouse.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a Java class as a ClickHouse materialized view descriptor.
 *
 * <p>Annotated classes are picked up by {@link io.blinkhouse.core.schema.SchemaManager}
 * and the DDL generator when the schema mode is {@code CREATE_IF_MISSING} or higher.
 *
 * <p>Example:
 * <pre>{@code
 * @ChMaterializedView(
 *     name = "daily_sales_mv",
 *     targetTable = "daily_sales",
 *     selectSql = "SELECT toDate(ts) AS day, sum(amount) AS total FROM sales GROUP BY day",
 *     populate = false
 * )
 * public class DailySalesMv {}
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ChMaterializedView {

    /** Name of the materialized view. Defaults to the class name in snake_case. */
    String name() default "";

    /** Database where the view is created. Defaults to the connection default. */
    String database() default "";

    /**
     * The destination table that receives rows from the SELECT.
     * If empty, an implicit storage table is created with the same name and a {@code _inner} suffix.
     */
    String targetTable() default "";

    /**
     * The SELECT statement that defines what rows flow into the view.
     * Must be a valid ClickHouse SELECT.
     */
    String selectSql();

    /**
     * Whether to add {@code POPULATE} to backfill existing data from the source table.
     * Defaults to {@code false} because POPULATE is not atomic and can cause duplicates.
     */
    boolean populate() default false;

    /** Optional {@code ON CLUSTER} clause for distributed DDL. */
    String onCluster() default "";
}

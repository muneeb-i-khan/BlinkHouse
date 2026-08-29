package io.blinkhouse.core.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * A single ClickHouse table {@code SETTINGS} key-value pair.
 *
 * <p>Used inside {@link ChTable#settings()} to pass arbitrary engine
 * settings to the {@code CREATE TABLE} DDL statement.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface ChSetting {

    /** Setting name, e.g. {@code "index_granularity"}. */
    String name();

    /** Setting value as a string, e.g. {@code "8192"}. */
    String value();
}

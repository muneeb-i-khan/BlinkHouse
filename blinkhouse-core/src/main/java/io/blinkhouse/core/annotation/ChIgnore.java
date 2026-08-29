package io.blinkhouse.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Excludes a field or record component from ClickHouse mapping.
 *
 * <p>Annotated members are invisible to the metadata resolver: they will not appear
 * in any generated DDL and will not be included in read or write operations.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.FIELD, ElementType.RECORD_COMPONENT })
public @interface ChIgnore {
}

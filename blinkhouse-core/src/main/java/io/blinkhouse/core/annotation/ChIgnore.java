package io.blinkhouse.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Excludes a field or record component from the ClickHouse mapping entirely.
 * The field is skipped during both reads and writes.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.FIELD, ElementType.RECORD_COMPONENT })
public @interface ChIgnore {
}

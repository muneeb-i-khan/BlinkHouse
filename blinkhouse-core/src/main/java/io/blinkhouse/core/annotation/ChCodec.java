package io.blinkhouse.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares per-column compression codecs for a ClickHouse column.
 *
 * <p>Codecs are applied in declaration order, e.g. {@code {"Delta", "ZSTD(3)"}} produces
 * {@code CODEC(Delta, ZSTD(3))} in the DDL. When absent, ClickHouse uses the table or
 * server default.
 *
 * <p>Example:
 * <pre>
 * {@literal @}ChCodec({"Delta", "ZSTD(3)"})
 * long counter;
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.FIELD, ElementType.RECORD_COMPONENT })
public @interface ChCodec {

    /**
     * Ordered list of codec names and optional arguments,
     * e.g. {@code {"Delta", "ZSTD(3)"}} or {@code {"LZ4"}}.
     */
    String[] value();
}

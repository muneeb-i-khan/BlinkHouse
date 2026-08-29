package io.blinkhouse.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Container annotation for repeatable {@link ChSkipIndex} declarations.
 *
 * <p>Not intended for direct use — Java generates this automatically when
 * {@link ChSkipIndex} is repeated on a type.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ChSkipIndexes {

    /** The contained skip-index declarations. */
    ChSkipIndex[] value();
}

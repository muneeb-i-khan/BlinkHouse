package io.blinkhouse.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a type or member as an internal BlinkHouse implementation detail.
 *
 * <p>Callers outside the {@code io.blinkhouse} package tree MUST NOT depend on
 * anything annotated with {@code @Internal}. Such types may change or be removed
 * in any release without a major version bump.
 *
 * <p>This annotation is enforced at build time by an ArchUnit rule in the
 * {@code blinkhouse-core} test module.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD})
public @interface Internal {
}

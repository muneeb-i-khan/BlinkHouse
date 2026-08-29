package io.blinkhouse.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a type or member as part of the stable BlinkHouse public API.
 *
 * <p>Types annotated with {@code @BlinkHouseApi} form the API-stability contract.
 * Breaking changes to these types require a major version bump.
 *
 * <p>Types NOT annotated with {@code @BlinkHouseApi} (including everything in
 * {@code *.internal.*} packages) are internal implementation details and may
 * change without notice between any release.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface BlinkHouseApi {
}

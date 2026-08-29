package io.blinkhouse.core.metadata;

/**
 * Extracts the value of one field/component from an entity instance.
 *
 * <p>Implementations back this with {@link java.lang.invoke.MethodHandle} or
 * {@link java.lang.invoke.LambdaMetafactory} for zero-reflection overhead at
 * call time (Phase 1). The Phase 2 implementation uses a simple reflective
 * accessor that is replaced by the full LambdaMetafactory path in Phase 1.
 *
 * @param <T> entity type
 */
@FunctionalInterface
public interface ValueAccessor<T> {

    /**
     * Returns the field value for {@code entity}.
     *
     * @throws RuntimeException if the underlying accessor fails (should not happen
     *                          after startup validation)
     */
    Object get(T entity);
}

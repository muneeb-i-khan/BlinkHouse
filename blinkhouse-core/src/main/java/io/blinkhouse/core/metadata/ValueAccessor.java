package io.blinkhouse.core.metadata;

/**
 * Extracts a single column value from an entity instance.
 *
 * <p>Implementations are produced by {@link EntityMetadataFactory} using
 * {@code LambdaMetafactory} or {@code MethodHandle} to avoid reflection in the hot path.
 *
 * @param <T> the entity type
 */
@FunctionalInterface
public interface ValueAccessor<T> {

    /**
     * Returns the column value from {@code entity}.
     *
     * @param entity the entity instance; must not be {@code null}
     * @return the column value; may be {@code null} if the column is nullable
     */
    Object get(T entity);
}

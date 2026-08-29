package io.blinkhouse.spring.repository;

import org.springframework.data.repository.core.EntityInformation;

/**
 * Spring Data {@link EntityInformation} for ClickHouse entities.
 *
 * <p>ClickHouse does not enforce a single-column primary key, so {@link #getId}
 * always returns {@code null} and {@link #getIdType} is unsupported.
 * Every entity is treated as new (append-only semantics).
 *
 * @param <T>  the entity type
 * @param <ID> the identifier type (may be {@code Void})
 */
public final class ChEntityInformation<T, ID> implements EntityInformation<T, ID> {

    private final Class<T> javaType;

    /**
     * Constructs entity information for the given domain class.
     *
     * @param javaType the entity class
     */
    public ChEntityInformation(Class<T> javaType) {
        this.javaType = javaType;
    }

    @Override
    public boolean isNew(T entity) {
        return true;
    }

    @Override
    public ID getId(T entity) {
        return null;
    }

    @Override
    public Class<ID> getIdType() {
        throw new UnsupportedOperationException(
            "ClickHouse entities have no single-column primary key. "
            + "Use @ChTable(orderBy=...) to define the sort key.");
    }

    @Override
    public Class<T> getJavaType() {
        return javaType;
    }
}

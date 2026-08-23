package io.blinkhouse.spring.repository;

/**
 * Base implementation of {@link ClickHouseRepository}.
 *
 * <p>In Spike C this class is a stub that proves the Spring Data factory
 * infrastructure can instantiate and wire a concrete implementation for any
 * user-defined sub-interface. Actual query and write operations are added
 * from Phase 1 onward.
 *
 * @param <T>  entity type
 * @param <ID> identifier type
 */
public class SimpleClickHouseRepository<T, ID> implements ClickHouseRepository<T, ID> {

    private final Class<T> entityType;

    public SimpleClickHouseRepository(Class<T> entityType) {
        this.entityType = entityType;
    }

    public Class<T> getEntityType() {
        return entityType;
    }
}

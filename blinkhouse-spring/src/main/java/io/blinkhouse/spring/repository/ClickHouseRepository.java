package io.blinkhouse.spring.repository;

import org.springframework.data.repository.Repository;

/**
 * Central repository interface for BlinkHouse.
 *
 * <p>Extends Spring Data's marker {@link Repository} so that
 * {@code @EnableClickHouseRepositories} can discover user-defined sub-interfaces
 * via Spring Data's repository scanning infrastructure.
 *
 * <p>Unlike JPA's {@code CrudRepository}, this interface deliberately exposes no
 * mutation methods at the marker level — BlinkHouse write operations are append-only
 * batch operations, not save/delete convenience methods.
 *
 * @param <T>  the entity type
 * @param <ID> the entity's identifier type
 */
public interface ClickHouseRepository<T, ID> extends Repository<T, ID> {
}

package io.blinkhouse.spring.repository;

import org.springframework.data.repository.core.support.RepositoryFactorySupport;
import org.springframework.data.repository.core.support.TransactionalRepositoryFactoryBeanSupport;

/**
 * Spring Data entry-point: registered by {@code @EnableClickHouseRepositories}
 * as the {@code repositoryFactoryBeanClass}.
 *
 * <p>Extends {@link TransactionalRepositoryFactoryBeanSupport} so that Spring's
 * repository scanning infrastructure recognises this as a valid factory bean.
 * BlinkHouse does not use transactions — the transactional support here is a
 * no-op; it exists only to satisfy the SPI.
 *
 * @param <T>  entity type
 * @param <ID> identifier type
 * @param <R>  repository type
 */
public class ClickHouseRepositoryFactoryBean<R extends ClickHouseRepository<T, ID>, T, ID>
        extends TransactionalRepositoryFactoryBeanSupport<R, T, ID> {

    protected ClickHouseRepositoryFactoryBean(Class<? extends R> repositoryInterface) {
        super(repositoryInterface);
    }

    @Override
    protected RepositoryFactorySupport doCreateRepositoryFactory() {
        return new ClickHouseRepositoryFactory();
    }
}

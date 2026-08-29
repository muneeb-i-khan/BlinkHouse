package io.blinkhouse.spring.repository;

import io.blinkhouse.core.template.ChTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;
import org.springframework.data.repository.core.support.TransactionalRepositoryFactoryBeanSupport;
import org.springframework.lang.Nullable;

/**
 * Spring Data entry-point: registered by {@code @EnableClickHouseRepositories}
 * as the {@code repositoryFactoryBeanClass}.
 *
 * <p>Extends {@link TransactionalRepositoryFactoryBeanSupport} so that Spring's
 * repository scanning infrastructure recognises this as a valid factory bean.
 * BlinkHouse does not use transactions — the transactional support is a no-op;
 * it exists only to satisfy the SPI.
 *
 * <p>The {@link ChTemplate} is optional: when absent (e.g. in pure-Spring tests
 * without a ClickHouse container) the factory falls back to stub mode, enabling
 * custom fragments without query derivation.
 *
 * @param <R>  repository type
 * @param <T>  entity type
 * @param <ID> identifier type
 */
public class ClickHouseRepositoryFactoryBean<R extends ClickHouseRepository<T, ID>, T, ID>
        extends TransactionalRepositoryFactoryBeanSupport<R, T, ID> {

    @Nullable
    private ChTemplate template;

    /**
     * Constructs the factory bean for the given repository interface.
     *
     * @param repositoryInterface the repository interface type
     */
    protected ClickHouseRepositoryFactoryBean(Class<? extends R> repositoryInterface) {
        super(repositoryInterface);
    }

    /**
     * Injects the {@link ChTemplate} from the application context.
     *
     * @param template the ChTemplate bean; may be {@code null} in stub mode
     */
    @Autowired(required = false)
    public void setTemplate(@Nullable ChTemplate template) {
        this.template = template;
    }

    @Override
    protected RepositoryFactorySupport doCreateRepositoryFactory() {
        return new ClickHouseRepositoryFactory(template);
    }
}

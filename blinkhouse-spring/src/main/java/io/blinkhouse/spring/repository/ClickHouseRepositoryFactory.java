package io.blinkhouse.spring.repository;

import org.springframework.data.repository.core.EntityInformation;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;
import org.springframework.data.repository.query.QueryLookupStrategy;
import org.springframework.data.repository.query.QueryMethodEvaluationContextProvider;

import java.util.Optional;

/**
 * Spring Data factory that creates repository proxies backed by
 * {@link SimpleClickHouseRepository}.
 *
 * <p>Spike C scope: every repository interface gets a
 * {@link SimpleClickHouseRepository} as its base implementation. No query
 * derivation, no method execution — just proof that the Spring Data SPI wires
 * correctly and that user-defined methods on the interface can be invoked via
 * the proxy.
 */
public class ClickHouseRepositoryFactory extends RepositoryFactorySupport {

    @Override
    @SuppressWarnings("unchecked")
    public <T, ID> EntityInformation<T, ID> getEntityInformation(Class<T> domainClass) {
        return new SimpleEntityInformation<>(domainClass);
    }

    @Override
    protected Object getTargetRepository(RepositoryInformation metadata) {
        return new SimpleClickHouseRepository<>(metadata.getDomainType());
    }

    @Override
    protected Class<?> getRepositoryBaseClass(RepositoryMetadata metadata) {
        return SimpleClickHouseRepository.class;
    }

    @Override
    protected Optional<QueryLookupStrategy> getQueryLookupStrategy(
            QueryLookupStrategy.Key key,
            QueryMethodEvaluationContextProvider evaluationContextProvider) {
        // No query derivation in Spike C — return empty to let the proxy
        // handle only the base-class methods.
        return Optional.empty();
    }

    /**
     * Minimal {@link EntityInformation} that carries only the domain class.
     * ID extraction is unsupported in the spike — ClickHouse entities rarely
     * have a single-column primary key anyway.
     */
    private static class SimpleEntityInformation<T, ID> implements EntityInformation<T, ID> {

        private final Class<T> domainType;

        SimpleEntityInformation(Class<T> domainType) {
            this.domainType = domainType;
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
            throw new UnsupportedOperationException("ID type not supported in Spike C");
        }

        @Override
        public Class<T> getJavaType() {
            return domainType;
        }
    }
}

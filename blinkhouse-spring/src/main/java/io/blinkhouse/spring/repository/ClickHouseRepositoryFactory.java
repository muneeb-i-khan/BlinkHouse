package io.blinkhouse.spring.repository;

import io.blinkhouse.core.metadata.EntityMetadataFactory;
import io.blinkhouse.core.template.ChTemplate;
import io.blinkhouse.core.type.TypeRegistry;
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
 * <p>When a {@link ChTemplate} is provided, derived query methods are resolved
 * through {@link ChQueryLookupStrategy}. Without a template (e.g. in Spike C tests)
 * the factory falls back to no query lookup strategy, which allows only custom
 * fragment methods.
 */
public class ClickHouseRepositoryFactory extends RepositoryFactorySupport {

    private final ChTemplate template;
    private final EntityMetadataFactory metadataFactory;

    /**
     * Constructs a factory backed by the given template.
     *
     * @param template the ChTemplate for query execution; may be {@code null} for stub mode
     */
    public ClickHouseRepositoryFactory(ChTemplate template) {
        this.template = template;
        this.metadataFactory = template != null
            ? new EntityMetadataFactory(TypeRegistry.withDefaults())
            : null;
    }

    /**
     * Constructs a factory in stub mode (no template, fragment dispatch only).
     */
    public ClickHouseRepositoryFactory() {
        this(null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T, ID> EntityInformation<T, ID> getEntityInformation(Class<T> domainClass) {
        return new ChEntityInformation<>(domainClass);
    }

    @Override
    protected Object getTargetRepository(RepositoryInformation metadata) {
        @SuppressWarnings("unchecked")
        Class<Object> domainType = (Class<Object>) metadata.getDomainType();
        if (template != null) {
            return new SimpleClickHouseRepository<>(domainType, template);
        }
        return new SimpleClickHouseRepository<>(domainType);
    }

    @Override
    protected Class<?> getRepositoryBaseClass(RepositoryMetadata metadata) {
        return SimpleClickHouseRepository.class;
    }

    @Override
    protected Optional<QueryLookupStrategy> getQueryLookupStrategy(
            QueryLookupStrategy.Key key,
            QueryMethodEvaluationContextProvider evaluationContextProvider) {
        if (template == null) {
            return Optional.empty();
        }
        return Optional.of(new ChQueryLookupStrategy(template, metadataFactory));
    }
}

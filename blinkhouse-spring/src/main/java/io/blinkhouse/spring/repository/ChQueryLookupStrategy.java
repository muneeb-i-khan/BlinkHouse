package io.blinkhouse.spring.repository;

import io.blinkhouse.core.metadata.EntityMetadata;
import io.blinkhouse.core.metadata.EntityMetadataFactory;
import io.blinkhouse.core.template.ChTemplate;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.repository.core.NamedQueries;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.query.QueryLookupStrategy;
import org.springframework.data.repository.query.QueryMethod;
import org.springframework.data.repository.query.RepositoryQuery;

import java.lang.reflect.Method;

/**
 * Routes each repository method to the appropriate query implementation:
 * <ul>
 *   <li>{@link Query @Query} annotated → {@link NativeChQuery}</li>
 *   <li>Otherwise → {@link PartTreeChQuery} (derived from method name)</li>
 * </ul>
 *
 * <p>Validation happens at context-refresh time: invalid property paths and
 * unsupported keywords throw at construction, not on first invocation.
 */
public final class ChQueryLookupStrategy implements QueryLookupStrategy {

    private final ChTemplate template;
    private final EntityMetadataFactory metadataFactory;

    /**
     * Constructs the strategy.
     *
     * @param template        the ChTemplate to execute queries against
     * @param metadataFactory the factory for resolving entity metadata
     */
    public ChQueryLookupStrategy(ChTemplate template, EntityMetadataFactory metadataFactory) {
        this.template = template;
        this.metadataFactory = metadataFactory;
    }

    @Override
    public RepositoryQuery resolveQuery(Method method, RepositoryMetadata metadata,
            ProjectionFactory factory, NamedQueries namedQueries) {
        QueryMethod queryMethod = new QueryMethod(method, metadata, factory);

        Query queryAnnotation = method.getAnnotation(Query.class);
        if (queryAnnotation != null) {
            return new NativeChQuery(queryMethod, method, template, queryAnnotation.value(),
                metadata.getDomainType());
        }

        @SuppressWarnings("unchecked")
        EntityMetadata<?> entityMetadata =
            metadataFactory.resolve((Class<Object>) metadata.getDomainType());
        return new PartTreeChQuery(queryMethod, template, entityMetadata);
    }
}

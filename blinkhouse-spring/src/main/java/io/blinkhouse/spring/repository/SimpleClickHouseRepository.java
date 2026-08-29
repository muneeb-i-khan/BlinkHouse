package io.blinkhouse.spring.repository;

import io.blinkhouse.core.exception.ChException;
import io.blinkhouse.core.metadata.EntityMetadata;
import io.blinkhouse.core.metadata.EntityMetadataFactory;
import io.blinkhouse.core.template.ChTemplate;
import io.blinkhouse.core.type.TypeRegistry;
import io.blinkhouse.spring.support.SpringChExceptionTranslator;
import org.springframework.dao.DataAccessException;

import java.util.Collection;
import java.util.List;

/**
 * Default implementation of {@link ClickHouseRepository}.
 *
 * <p>All write operations are append-only. There is no update or delete
 * at the row level — ClickHouse mutations are issued via native SQL only.
 *
 * @param <T>  entity type
 * @param <ID> identifier type (structural, not used for key lookups)
 */
public class SimpleClickHouseRepository<T, ID> implements ClickHouseRepository<T, ID> {

    private final Class<T> entityType;
    private final ChTemplate template;
    private final EntityMetadata<T> entityMetadata;

    /**
     * Constructs the repository with an explicit {@link ChTemplate}.
     *
     * <p>Entity metadata is resolved lazily on first use so that repositories for
     * entities without {@code @ChTable} can still be wired as fragments.
     *
     * @param entityType the entity class
     * @param template   the ChTemplate to delegate operations to
     */
    public SimpleClickHouseRepository(Class<T> entityType, ChTemplate template) {
        this.entityType = entityType;
        this.template = template;
        this.entityMetadata = null; // resolved lazily
    }

    /**
     * Constructs a no-template stub (Spike C compatibility).
     *
     * @param entityType the entity class
     * @deprecated Use {@link #SimpleClickHouseRepository(Class, ChTemplate)} instead.
     */
    @Deprecated
    public SimpleClickHouseRepository(Class<T> entityType) {
        this.entityType = entityType;
        this.template = null;
        this.entityMetadata = null;
    }

    /**
     * Returns the entity class this repository manages.
     *
     * @return the entity class
     */
    public Class<T> getEntityType() {
        return entityType;
    }

    /**
     * Inserts a single entity via a direct HTTP POST.
     *
     * <p><strong>Anti-pattern:</strong> Use {@link ChTemplate#batchWriter} for production
     * ingest. This method is provided for convenience and testing.
     *
     * @param entity the entity to insert
     * @throws DataAccessException on ClickHouse error
     */
    public void insert(T entity) {
        requireTemplate();
        try {
            template.insertSingleRow(entity);
        } catch (ChException ex) {
            throw SpringChExceptionTranslator.translate(ex);
        }
    }

    /**
     * Inserts a collection of entities in a single RowBinary HTTP POST.
     *
     * @param entities the rows to insert
     * @throws DataAccessException on ClickHouse error
     */
    public void insertAll(Collection<? extends T> entities) {
        requireTemplate();
        try {
            @SuppressWarnings("unchecked")
            Collection<T> rows = (Collection<T>) entities;
            template.insert(entityType, rows);
        } catch (ChException ex) {
            throw SpringChExceptionTranslator.translate(ex);
        }
    }

    /**
     * Returns all rows from the entity's table.
     *
     * <p>No LIMIT is applied. Use derived query methods or {@link Query @Query} for
     * filtered / paginated reads.
     *
     * @return all rows, never {@code null}
     * @throws DataAccessException on ClickHouse error
     */
    public List<T> findAll() {
        requireTemplate();
        String sql = "SELECT * FROM " + metadata().getQualifiedName();
        try {
            return template.queryForList(entityType, sql);
        } catch (ChException ex) {
            throw SpringChExceptionTranslator.translate(ex);
        }
    }

    /**
     * Returns the total row count for the entity's table.
     *
     * @return the count
     * @throws DataAccessException on ClickHouse error
     */
    public long count() {
        requireTemplate();
        String sql = "SELECT count() FROM " + metadata().getQualifiedName();
        try {
            List<Long> result = template.queryForList(Long.class, sql);
            return result.isEmpty() ? 0L : result.get(0);
        } catch (ChException ex) {
            throw SpringChExceptionTranslator.translate(ex);
        }
    }

    private EntityMetadata<T> metadata() {
        if (entityMetadata != null) {
            return entityMetadata;
        }
        EntityMetadataFactory factory = new EntityMetadataFactory(TypeRegistry.withDefaults());
        return factory.resolve(entityType);
    }

    private void requireTemplate() {
        if (template == null) {
            throw new IllegalStateException(
                "ChTemplate is not configured. Ensure the BlinkHouse starter is on the "
                + "classpath and clickhouse.url is set in application.yml.");
        }
    }
}

package io.blinkhouse.core.schema;

import io.blinkhouse.core.metadata.DictionaryMetadata;
import io.blinkhouse.core.metadata.EntityMetadata;
import io.blinkhouse.core.metadata.MaterializedViewMetadata;
import java.util.List;

/**
 * Generates ClickHouse DDL statements from entity metadata.
 *
 * <p>Implementations must be stateless and thread-safe.
 */
public interface DdlGenerator {

    /**
     * Generates a {@code CREATE TABLE} statement for the given entity.
     *
     * @param metadata    the resolved entity metadata
     * @param ifNotExists whether to emit {@code IF NOT EXISTS}
     * @return the complete DDL string
     */
    String createTable(EntityMetadata<?> metadata, boolean ifNotExists);

    /**
     * Generates the {@code ALTER TABLE} statements required to apply the given diff.
     *
     * @param metadata the entity metadata (for the qualified table name)
     * @param changes  the schema changes to apply
     * @return ordered list of ALTER TABLE statements; empty if the changes list is empty
     */
    List<String> alterStatements(EntityMetadata<?> metadata, List<SchemaChange> changes);

    /**
     * Generates a {@code CREATE MATERIALIZED VIEW} statement.
     *
     * @param mv          the materialized view descriptor
     * @param ifNotExists whether to emit {@code IF NOT EXISTS}
     * @return the complete DDL string
     */
    String createMaterializedView(MaterializedViewMetadata mv, boolean ifNotExists);

    /**
     * Generates a {@code CREATE DICTIONARY} statement.
     *
     * @param dict        the dictionary descriptor
     * @param ifNotExists whether to emit {@code IF NOT EXISTS}
     * @return the complete DDL string
     */
    String createDictionary(DictionaryMetadata dict, boolean ifNotExists);
}

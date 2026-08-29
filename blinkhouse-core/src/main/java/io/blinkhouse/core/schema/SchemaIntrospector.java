package io.blinkhouse.core.schema;

import java.util.List;
import java.util.Optional;

/**
 * Reads the live schema state from a running ClickHouse instance.
 *
 * <p>Implementations query {@code system.tables}, {@code system.columns},
 * and {@code system.data_skipping_indices}.
 */
public interface SchemaIntrospector {

    /**
     * Returns a complete description of a table, or empty if the table does not exist.
     *
     * @param database  the database name
     * @param tableName the table name
     * @return the live table descriptor, or empty if the table does not exist
     */
    Optional<LiveTable> describe(String database, String tableName);

    /**
     * Returns all columns for a table in physical order.
     *
     * @param database  the database name
     * @param tableName the table name
     * @return the list of live columns
     */
    List<LiveColumn> columns(String database, String tableName);

    /**
     * Returns all data-skipping indexes for a table.
     *
     * @param database  the database name
     * @param tableName the table name
     * @return the list of live indexes
     */
    List<LiveIndex> indexes(String database, String tableName);
}

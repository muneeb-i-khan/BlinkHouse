package io.blinkhouse.core.mapping;

import io.blinkhouse.core.exception.ChMappingException;

import java.util.Map;

/**
 * Maps a single result row (represented as a column-name → raw-string map) to a typed object.
 *
 * @param <T> the target type
 */
@FunctionalInterface
public interface RowMapper<T> {

    /**
     * Maps one result row to an instance of {@code T}.
     *
     * @param row a map of column name → raw TSV value for this row
     * @return the mapped object (must not be {@code null})
     * @throws ChMappingException if the row cannot be mapped
     */
    T mapRow(Map<String, String> row) throws ChMappingException;
}

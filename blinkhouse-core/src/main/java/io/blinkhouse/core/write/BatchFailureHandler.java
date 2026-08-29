package io.blinkhouse.core.write;

import io.blinkhouse.core.exception.ChException;
import java.util.List;

/**
 * Callback invoked when a batch has exhausted all retry attempts without success.
 *
 * <p>Implementations typically write the rows to a dead-letter file, publish to a queue,
 * or log a structured alert. The callback must be fast and must not throw.
 *
 * @param <T> the entity type
 */
@FunctionalInterface
public interface BatchFailureHandler<T> {

    /**
     * Called with the rows that could not be ingested after all retry attempts.
     *
     * @param rows     the rows that were not delivered to ClickHouse
     * @param cause    the last exception that caused the failure
     * @param attempts the total number of delivery attempts made
     */
    void onFailure(List<T> rows, ChException cause, int attempts);
}

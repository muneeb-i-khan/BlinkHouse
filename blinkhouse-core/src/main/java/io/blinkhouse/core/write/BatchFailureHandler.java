package io.blinkhouse.core.write;

import io.blinkhouse.core.exception.ChException;

import java.util.List;

/**
 * Dead-letter callback invoked by the {@link BatchWriter} flusher when a batch
 * exhausts all retry attempts and cannot be delivered to ClickHouse.
 *
 * <p>Implementations must be fast and non-blocking — the flusher thread calls
 * this inline. Write dead letters to an external queue or log asynchronously.
 *
 * <p><strong>No row is ever silently dropped.</strong> If no handler is configured
 * the default implementation logs at ERROR. (NFR-7)
 *
 * @param <T> entity type
 */
@FunctionalInterface
public interface BatchFailureHandler<T> {

    /**
     * Called when {@code rows} could not be ingested after {@code attempts} tries.
     *
     * @param rows     the failed rows in original insertion order
     * @param cause    the final exception that ended retries
     * @param attempts total number of attempts made (≥ 1)
     */
    void onFailure(List<T> rows, ChException cause, int attempts);
}

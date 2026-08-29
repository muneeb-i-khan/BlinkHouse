package io.blinkhouse.core.observability;

/**
 * SPI for recording BlinkHouse operational metrics.
 *
 * <p>Implementations are injected into {@link io.blinkhouse.core.template.ChTemplate}
 * and {@link io.blinkhouse.core.write.BatchWriter}. The default implementation is
 * {@link NoopChMetrics}; Micrometer wiring is provided by the Spring Boot starter.
 *
 * <p>All tag values are safe identifiers — never raw SQL or user input (NFR-6).
 */
public interface ChMetrics {

    /**
     * Records a completed query execution.
     *
     * @param table      the target table name (or {@code "unknown"})
     * @param operation  the operation type: {@code "select"}, {@code "insert"}, etc.
     * @param repository the Spring Data repository simple name, or {@code "none"}
     * @param method     the repository method name, or {@code "none"}
     * @param outcome    {@code "success"} or {@code "error"}
     * @param durationMs elapsed wall-clock time in milliseconds
     */
    void recordQuery(String table, String operation, String repository,
                     String method, String outcome, long durationMs);

    /**
     * Records a completed batch flush.
     *
     * @param table      the target table name
     * @param rows       number of rows flushed
     * @param bytes      number of bytes written
     * @param outcome    {@code "success"} or {@code "error"}
     * @param durationMs elapsed wall-clock time in milliseconds
     */
    void recordBatch(String table, long rows, long bytes, String outcome, long durationMs);

    /**
     * Records rows written to the dead-letter store after exhausted retries.
     *
     * @param table the target table name
     * @param rows  the number of dead-lettered rows
     */
    void recordDeadLetter(String table, long rows);

    /**
     * Updates the buffer occupancy gauges for a named writer.
     *
     * @param table     the target table name
     * @param buffRows  current number of rows buffered
     * @param buffBytes current number of bytes buffered
     */
    void recordBufferOccupancy(String table, long buffRows, long buffBytes);

    /**
     * Records a single-row insert anti-pattern occurrence (P2).
     *
     * @param table the target table name
     */
    void recordSingleRowInsert(String table);
}

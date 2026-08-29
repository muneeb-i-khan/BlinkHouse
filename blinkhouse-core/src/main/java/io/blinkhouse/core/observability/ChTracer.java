package io.blinkhouse.core.observability;

/**
 * SPI for distributed tracing integration.
 *
 * <p>Implementations wrap the tracing backend (e.g. Micrometer Tracing / OpenTelemetry).
 * The default implementation is {@link NoopChTracer}.
 *
 * <p><strong>Security invariant (NFR-6):</strong> SQL attached to spans must be in
 * parameterised form only — parameter values must never appear in span attributes.
 */
public interface ChTracer {

    /**
     * Starts a new span for a ClickHouse operation.
     *
     * <p>The caller must always pair this with {@link #endSpan(Object, Throwable)}.
     *
     * @param operationName a short descriptive name (e.g. {@code "ch.select"})
     * @param parameterisedSql the SQL with placeholders, never with bound values
     * @param queryId          the correlatable query ID
     * @return an opaque span handle to pass to {@link #endSpan}; never {@code null}
     */
    Object startSpan(String operationName, String parameterisedSql, String queryId);

    /**
     * Ends the span returned by {@link #startSpan}.
     *
     * @param spanHandle the handle returned by {@code startSpan}
     * @param error      non-null if the operation failed; {@code null} on success
     */
    void endSpan(Object spanHandle, Throwable error);
}

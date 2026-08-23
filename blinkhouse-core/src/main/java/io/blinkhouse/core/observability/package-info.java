/**
 * Observability SPI — metrics, tracing, and query-ID propagation.
 *
 * <p>{@code query_id} format: {@code blinkhouse-{appName}-{traceId|uuid}}, enabling users
 * to join application traces against {@code system.query_log} (NFR-10).
 *
 * <p>SQL in spans is always in parameterised form — parameter values are never attached
 * to spans or log lines (NFR-6).
 *
 * <ul>
 *   <li>{@code ChMetrics} — SPI: recordQuery, recordBatch, recordDeadLetter, recordBufferOccupancy</li>
 *   <li>{@code ChTracer} — SPI: startSpan, endSpan with sanitised SQL tag</li>
 *   <li>{@code QueryIdGenerator} — produces correlatable query IDs</li>
 * </ul>
 */
package io.blinkhouse.core.observability;

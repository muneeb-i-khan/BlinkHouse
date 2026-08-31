/**
 * Write path and high-throughput batch ingestion.
 *
 * <p>{@code BatchWriter} is the primary write API. Single-row {@code insert(T)} exists,
 * is instrumented as an anti-pattern (increments {@code blinkhouse.insert.singlerow}
 * counter and logs WARN), and is documented as such (design principle P2).
 *
 * <p>No silent data loss path exists. Every row either lands in ClickHouse or is
 * delivered to the {@code BatchFailureHandler} (dead-letter callback). NFR-7.
 *
 * <ul>
 *   <li>{@code BatchWriter} — bounded MPSC queue, flush triggers, retry, drain-on-shutdown</li>
 *   <li>{@code BatchWriterConfig} — maxRows, maxBytes, flushInterval, backpressure, retry, drainTimeout</li>
 *   <li>{@code BackpressurePolicy} — {@code BLOCK} / {@code DROP_OLDEST} / {@code FAIL}</li>
 *   <li>{@code RetryPolicy} — exponential backoff with jitter, max attempts</li>
 *   <li>{@code ErrorClassifier} — RETRYABLE vs TERMINAL classification by ClickHouse error code</li>
 *   <li>{@code BatchFailureHandler} — dead-letter callback: failed rows + cause + attempt count</li>
 *   <li>{@code FlushTrigger} — row count / byte size / interval conditions</li>
 * </ul>
 */
package io.blinkhouse.core.write;

package io.blinkhouse.benchmark;

import java.util.UUID;

/**
 * Immutable record matching the {@code bh_transport_benchmark} table schema.
 *
 * <p>Column mapping:
 * <pre>
 *   tenant_id   UInt32              → tenantId   int
 *   ts          DateTime64(3,'UTC') → tsMillis   long  (epoch-millis)
 *   user_id     UUID                → userId     UUID
 *   country     LowCardinality(String) → country String
 *   duration_ms UInt32              → durationMs int
 * </pre>
 */
public record TransportBenchmarkRow(int tenantId, long tsMillis, UUID userId, String country, int durationMs) {}

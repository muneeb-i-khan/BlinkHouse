/**
 * Spring Boot auto-configuration for BlinkHouse.
 *
 * <ul>
 *   <li>{@code BlinkHouseAutoConfiguration} — wires {@code ChConnectionProvider},
 *       {@code TypeRegistry}, {@code ChTemplate}, and {@code SchemaManager} beans</li>
 *   <li>{@code BlinkHouseProperties} — {@code clickhouse.*} configuration properties</li>
 *   <li>{@code BlinkHouseHealthIndicator} — Actuator health check ({@code SELECT 1},
 *       server version, replica lag)</li>
 *   <li>{@code BlinkHouseMetricsAutoConfiguration} — wires Micrometer metrics when present</li>
 * </ul>
 *
 * <p>Bean ordering: {@code SchemaManager} is guaranteed to run before any
 * {@code BatchWriter} bean via {@code @DependsOn}, preventing a race between
 * table creation and the first insert.
 */
package io.blinkhouse.boot;

/**
 * Test support module for BlinkHouse-based applications.
 *
 * <p>Add this to {@code test} scope only — it is not intended for production classpath.
 *
 * <ul>
 *   <li>{@code BlinkHouseTest} — slice annotation that boots only the BlinkHouse
 *       layer (no full web context); reuses a shared Testcontainers instance across tests</li>
 *   <li>{@code BhTestContainer} — singleton Testcontainers ClickHouse instance</li>
 *   <li>{@code FixtureLoader} — loads test data from CSV or JSON files</li>
 *   <li>{@code TableTruncator} — truncates specified tables between tests</li>
 * </ul>
 *
 * <p>Target: {@code @BlinkHouseTest} slice boots in under 10 seconds with a reused container.
 */
package io.blinkhouse.test;

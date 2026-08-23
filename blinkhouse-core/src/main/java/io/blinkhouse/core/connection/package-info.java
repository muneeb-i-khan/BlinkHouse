/**
 * Connection management SPI and pooling.
 *
 * <ul>
 *   <li>{@code ChConnectionProvider} — SPI for acquiring/releasing connections</li>
 *   <li>{@code ChConnection} — represents one open connection to ClickHouse</li>
 *   <li>{@code ChClientOptions} — timeouts, compression, TLS, per-query settings</li>
 *   <li>{@code NativeConnectionProvider} — client-v2 native protocol implementation</li>
 *   <li>{@code JdbcConnectionProvider} — JDBC-based implementation for tooling compat</li>
 *   <li>{@code ChConnectionPool} — bounded fair pool with leak detection</li>
 * </ul>
 */
package io.blinkhouse.core.connection;

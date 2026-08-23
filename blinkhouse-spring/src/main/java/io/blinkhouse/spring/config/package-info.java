/**
 * Spring configuration support.
 *
 * <ul>
 *   <li>{@code EnableClickHouseRepositories} — activates repository scanning
 *       (auto-enabled by the starter)</li>
 *   <li>{@code ChRepositoryRegistrar} — registers a {@code ClickHouseRepositoryFactoryBean}
 *       for each scanned repository interface</li>
 * </ul>
 */
package io.blinkhouse.spring.config;

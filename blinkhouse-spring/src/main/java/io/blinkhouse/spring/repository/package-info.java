/**
 * Spring Data repository infrastructure for BlinkHouse.
 *
 * <ul>
 *   <li>{@code ClickHouseRepository} — base repository interface ({@code insert}, {@code insertAll},
 *       {@code findAll}, {@code count}, {@code stream}, {@code Slice} pagination)</li>
 *   <li>{@code SimpleClickHouseRepository} — default implementation</li>
 *   <li>{@code ClickHouseRepositoryFactory} — creates repository proxies</li>
 *   <li>{@code ClickHouseRepositoryFactoryBean} — Spring bean wrapper</li>
 *   <li>{@code ChQueryLookupStrategy} — routes methods to {@code PartTreeChQuery} or {@code NativeChQuery}</li>
 *   <li>{@code PartTreeChQuery} — translates Spring Data PartTree → BlinkHouse AST</li>
 *   <li>{@code NativeChQuery} — executes {@code @Query}-annotated methods</li>
 *   <li>{@code ChEntityInformation} — entity metadata bridge for Spring Data SPI</li>
 * </ul>
 */
package io.blinkhouse.spring.repository;

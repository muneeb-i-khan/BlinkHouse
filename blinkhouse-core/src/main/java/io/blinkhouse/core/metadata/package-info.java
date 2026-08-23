/**
 * Entity metadata resolution and caching.
 *
 * <p>All reflection is resolved once at startup into immutable {@code EntityMetadata}
 * objects. Column accessors are compiled via {@code LambdaMetafactory} — no
 * {@code Field.get()} on the hot path (NFR-2, NFR-4).
 *
 * <ul>
 *   <li>{@code EntityMetadata} — immutable descriptor for a mapped class</li>
 *   <li>{@code ColumnMetadata} — per-column descriptor including the bound TypeHandler</li>
 *   <li>{@code EngineMetadata} — engine type + engine-specific parameters</li>
 *   <li>{@code EntityMetadataResolver} — resolves and caches EntityMetadata at startup</li>
 *   <li>{@code NamingStrategy} — pluggable column/table name conversion (SnakeCase, AsIs, …)</li>
 *   <li>{@code ValueAccessor} — MethodHandle-backed getter/setter, record component accessor</li>
 * </ul>
 */
package io.blinkhouse.core.metadata;

/**
 * Annotations for mapping Java types to ClickHouse tables and columns.
 *
 * <ul>
 *   <li>{@code @ChTable} — maps a class or record to a ClickHouse table</li>
 *   <li>{@code @ChColumn} — per-column name, type override, nullability, expressions</li>
 *   <li>{@code @ChEngine} — MergeTree engine variant and engine-specific parameters</li>
 *   <li>{@code @ChCodec} — per-column compression codecs (ZSTD, Delta, Gorilla, …)</li>
 *   <li>{@code @ChSkipIndex} — data-skipping index declarations</li>
 *   <li>{@code @ChTtl} — column and table TTL expressions</li>
 *   <li>{@code @ChNested} — Nested structure mapping</li>
 *   <li>{@code @ChSettings} — table-level SETTINGS overrides</li>
 *   <li>{@code @ChIgnore} — exclude a field from mapping</li>
 *   <li>{@code @ChEnumerated} — Java enum ↔ ClickHouse Enum8/Enum16 strategy</li>
 * </ul>
 */
package io.blinkhouse.core.annotation;

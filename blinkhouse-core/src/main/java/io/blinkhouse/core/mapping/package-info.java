/**
 * Row mapping — converts a {@code RowBinaryReader} row into a Java object.
 *
 * <p>{@code EntityRowMapper} binds by name once (in {@code init()}) then uses an
 * index-driven loop in {@code map()} with zero name lookups per row.
 *
 * <ul>
 *   <li>{@code RowMapper} — functional SPI: one row → one object</li>
 *   <li>{@code RowMappers} — factory: {@code forEntity(Class)}, {@code forRecord(Class)}, etc.</li>
 *   <li>{@code EntityRowMapper} — entity-bound mapper using MethodHandle accessors</li>
 *   <li>{@code RecordRowMapper} — canonical-constructor invocation via MethodHandle</li>
 *   <li>{@code SingleColumnRowMapper} — scalar projection</li>
 *   <li>{@code MapRowMapper} — {@code Map<String, Object>} projection</li>
 *   <li>{@code ProjectionRowMapper} — DTO/record projection by column alias</li>
 * </ul>
 */
package io.blinkhouse.core.mapping;

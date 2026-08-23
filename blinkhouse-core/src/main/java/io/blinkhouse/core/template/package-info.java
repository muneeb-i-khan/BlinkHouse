/**
 * ChTemplate — the central execution facade (L4 in the layered architecture).
 *
 * <p>Every path (repository derived methods, {@code @Query}, DSL, native SQL) funnels
 * through {@code ChTemplate}. This is how P4 (escape-hatch parity) is achieved
 * structurally: native SQL and the DSL share identical binding, mapping, metrics,
 * and exception translation.
 *
 * <ul>
 *   <li>{@code ChOperations} — interface: query, stream, queryForObject, execute, insert, batchWriter</li>
 *   <li>{@code ChTemplate} — stateless, thread-safe singleton implementation</li>
 *   <li>{@code ChStream} — {@code Stream<T>} backed by a cursor; must be closed (try-with-resources)</li>
 *   <li>{@code QueryContext} — per-execution context: queryId, sql, params, deadline</li>
 * </ul>
 */
package io.blinkhouse.core.template;

/**
 * ClickHouse type system — the highest-risk, highest-leverage component.
 *
 * <p>Build and property-test this package before anything else. A bug in UUID byte
 * ordering or DateTime64 timezone handling propagates as silent data corruption
 * everywhere else in the library.
 *
 * <ul>
 *   <li>{@code ClickHouseType} — sealed interface representing the ClickHouse type tree</li>
 *   <li>{@code TypeParser} — recursive-descent parser: {@code "Map(String, Array(Nullable(UInt32)))"} → tree</li>
 *   <li>{@code TypeHandler} — SPI: declare CH type, write to RowBinary, read from RowBinary</li>
 *   <li>{@code TypeRegistry} — ordered registry; user handlers (priority=100) override built-ins (priority=0)</li>
 *   <li>{@code handler.*} — built-in handlers for every FR-2 Must type</li>
 * </ul>
 *
 * <p><strong>Three silent-corruption traps:</strong>
 * <ol>
 *   <li>UUID: ClickHouse byte order ≠ RFC 4122. The handler swaps both 64-bit halves.</li>
 *   <li>DateTime64: timezone comes from the column type, never from {@code ZoneId.systemDefault()}.</li>
 *   <li>FixedString: right-pad with {@code \0} on write; strip trailing {@code \0} on read.</li>
 * </ol>
 */
package io.blinkhouse.core.type;

/**
 * RowBinary wire protocol codec.
 *
 * <p>Write format: {@code RowBinary} (header-free, column order controlled by entity metadata).
 * Read format: {@code RowBinaryWithNamesAndTypes} (header enables bind-by-name and
 * query-time drift detection).
 *
 * <ul>
 *   <li>{@code RowBinaryWriter} — serialises a {@code Collection<T>} directly into ClickHouse's binary format</li>
 *   <li>{@code RowBinaryReader} — cursor over a RowBinaryWithNamesAndTypes response stream</li>
 *   <li>{@code ChOutputStream} / {@code ChInputStream} — typed helpers for the binary format</li>
 *   <li>{@code ColumnBlock} — in-memory block with byte-bounded sizing</li>
 *   <li>{@code LEB128} — varint encoding helper for string and array lengths</li>
 * </ul>
 *
 * <p>Target block size: 32 MB (configurable), aligned with ClickHouse's preference for large inserts.
 * Buffers are pooled per flusher thread to minimise allocation on the write hot path.
 */
package io.blinkhouse.core.protocol;

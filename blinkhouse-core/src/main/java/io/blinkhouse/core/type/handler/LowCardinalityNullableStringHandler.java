package io.blinkhouse.core.type.handler;

import io.blinkhouse.core.protocol.ChInputStream;
import io.blinkhouse.core.protocol.ChOutputStream;
import io.blinkhouse.core.type.TypeHandler;

import java.io.IOException;
import java.util.Optional;

/**
 * Handles ClickHouse {@code LowCardinality(Nullable(String))} ↔ Java {@link Optional}{@code <String>}.
 *
 * <p><strong>SPIKE SIMPLIFICATION:</strong> The real ClickHouse LowCardinality wire format uses a
 * dictionary-based codec with shared dictionaries, index types, and special framing headers
 * (see ClickHouse source: DataTypeLowCardinality serialization). This implementation intentionally
 * skips the dictionary protocol and treats the wire format as plain nullable string in order to
 * prove that the Java-type round-trip (Optional&lt;String&gt; → bytes → Optional&lt;String&gt;) works
 * correctly during Spike B.
 *
 * <p><strong>TODO (production):</strong> Replace this implementation with the full dictionary
 * codec that handles:
 * <ul>
 *   <li>Serialization version prefix (UInt64)</li>
 *   <li>Dictionary type flags (global/local, with/without additional keys)</li>
 *   <li>Index type (UInt8, UInt16, UInt32, UInt64 depending on cardinality)</li>
 *   <li>Keys block (the actual string values)</li>
 *   <li>Indices block (references into the keys block)</li>
 * </ul>
 *
 * <p>Wire format used in this spike (ClickHouse Nullable(T) RowBinary convention):
 * <ul>
 *   <li>{@code 0x01} → null ({@code Optional.empty()})</li>
 *   <li>{@code 0x00} followed by LEB128 length + UTF-8 bytes → non-null value</li>
 * </ul>
 * Note: ClickHouse RowBinary encodes the null-flag byte as 1=null, 0=non-null.
 */
public final class LowCardinalityNullableStringHandler implements TypeHandler<Optional<String>> {

    @Override
    public String clickHouseTypeName() {
        return "LowCardinality(Nullable(String))";
    }

    @Override
    public void write(ChOutputStream out, Optional<String> value) throws IOException {
        if (value.isEmpty()) {
            out.writeByte(0x01); // 1 = null in ClickHouse Nullable RowBinary
        } else {
            out.writeByte(0x00); // 0 = non-null
            out.writeString(value.get());
        }
    }

    @Override
    public Optional<String> read(ChInputStream in) throws IOException {
        byte nullFlag = in.readByte();
        if (nullFlag == 0x01) {
            return Optional.empty();
        }
        return Optional.of(in.readString());
    }
}

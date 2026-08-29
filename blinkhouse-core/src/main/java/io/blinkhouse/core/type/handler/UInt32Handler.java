package io.blinkhouse.core.type.handler;

import io.blinkhouse.core.protocol.ChInputStream;
import io.blinkhouse.core.protocol.ChOutputStream;
import io.blinkhouse.core.type.TypeHandler;

import java.io.IOException;

/**
 * Handles ClickHouse {@code UInt32} ↔ Java {@link Long}.
 *
 * <p>Wire format: 4 bytes, little-endian unsigned integer.
 * Stored in a Java {@code long} to avoid sign loss (max UInt32 = 4,294,967,295).
 */
public final class UInt32Handler implements TypeHandler<Long> {

    @Override
    public String clickHouseTypeName() {
        return "UInt32";
    }

    @Override
    public void write(ChOutputStream out, Long value) throws IOException {
        int v = (int) (value == null ? 0L : (value & 0xFFFFFFFFL));
        out.writeInt(v);
    }

    @Override
    public Long read(ChInputStream in) throws IOException {
        return (long) in.readInt() & 0xFFFFFFFFL;
    }
}

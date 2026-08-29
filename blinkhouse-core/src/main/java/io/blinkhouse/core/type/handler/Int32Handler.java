package io.blinkhouse.core.type.handler;

import io.blinkhouse.core.protocol.ChInputStream;
import io.blinkhouse.core.protocol.ChOutputStream;
import io.blinkhouse.core.type.TypeHandler;

import java.io.IOException;

/**
 * Handles ClickHouse {@code Int32} ↔ Java {@link Integer}.
 *
 * <p>Wire format: 4 bytes, little-endian signed integer.
 */
public final class Int32Handler implements TypeHandler<Integer> {

    @Override
    public String clickHouseTypeName() {
        return "Int32";
    }

    @Override
    public void write(ChOutputStream out, Integer value) throws IOException {
        out.writeInt(value == null ? 0 : value);
    }

    @Override
    public Integer read(ChInputStream in) throws IOException {
        return in.readInt();
    }
}

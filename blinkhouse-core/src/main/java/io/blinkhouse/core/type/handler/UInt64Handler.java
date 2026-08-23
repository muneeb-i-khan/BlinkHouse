package io.blinkhouse.core.type.handler;

import io.blinkhouse.core.protocol.ChInputStream;
import io.blinkhouse.core.protocol.ChOutputStream;
import io.blinkhouse.core.type.TypeHandler;

import java.io.IOException;

/**
 * Handles ClickHouse {@code UInt64} ↔ Java {@code long}.
 *
 * <p>Wire format: 8 bytes, little-endian. Java's {@code long} is signed but the
 * bit pattern is preserved, so values in [0, Long.MAX_VALUE] are lossless.
 * Values in (Long.MAX_VALUE, UInt64.MAX_VALUE] are stored with the correct bit
 * pattern but will appear negative when interpreted as a signed Java long.
 * Callers that need unsigned semantics should use {@link Long#toUnsignedString}.
 */
public final class UInt64Handler implements TypeHandler<Long> {

    @Override
    public String clickHouseTypeName() {
        return "UInt64";
    }

    @Override
    public void write(ChOutputStream out, Long value) throws IOException {
        out.writeLong(value);
    }

    @Override
    public Long read(ChInputStream in) throws IOException {
        return in.readLong();
    }
}

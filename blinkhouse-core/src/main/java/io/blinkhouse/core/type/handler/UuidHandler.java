package io.blinkhouse.core.type.handler;

import io.blinkhouse.core.protocol.ChInputStream;
import io.blinkhouse.core.protocol.ChOutputStream;
import io.blinkhouse.core.type.TypeHandler;

import java.io.IOException;
import java.util.UUID;

/**
 * Handles ClickHouse {@code UUID} ↔ Java {@link UUID}.
 *
 * <p><strong>CRITICAL — wire format:</strong> ClickHouse stores UUID as two 64-bit
 * little-endian integers. The first int64 is the most-significant 64 bits of the UUID
 * and the second is the least-significant 64 bits. On the wire:
 * <pre>
 *   writeLong(uuid.getMostSignificantBits())   // bytes 0–7 LE
 *   writeLong(uuid.getLeastSignificantBits())  // bytes 8–15 LE
 * </pre>
 *
 * <p>This differs from the standard RFC 4122 big-endian representation. Getting
 * the byte order wrong produces a silently incorrect UUID that differs from the
 * original, making it important to test with a known value (see SpikeB_TypeRoundTripIT).
 */
public final class UuidHandler implements TypeHandler<UUID> {

    @Override
    public String clickHouseTypeName() {
        return "UUID";
    }

    @Override
    public void write(ChOutputStream out, UUID value) throws IOException {
        out.writeLong(value.getMostSignificantBits());
        out.writeLong(value.getLeastSignificantBits());
    }

    @Override
    public UUID read(ChInputStream in) throws IOException {
        long msb = in.readLong();
        long lsb = in.readLong();
        return new UUID(msb, lsb);
    }
}

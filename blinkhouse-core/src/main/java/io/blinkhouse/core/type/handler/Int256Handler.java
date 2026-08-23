package io.blinkhouse.core.type.handler;

import io.blinkhouse.core.protocol.ChInputStream;
import io.blinkhouse.core.protocol.ChOutputStream;
import io.blinkhouse.core.type.TypeHandler;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;

/**
 * Handles ClickHouse {@code Int256} ↔ Java {@link BigInteger}.
 *
 * <p>Wire format: 32 bytes, little-endian two's complement.
 * ClickHouse Int256 range: [−2^255, 2^255 − 1].
 */
public final class Int256Handler implements TypeHandler<BigInteger> {

    private static final int BYTES = 32;

    @Override
    public String clickHouseTypeName() {
        return "Int256";
    }

    @Override
    public void write(ChOutputStream out, BigInteger value) throws IOException {
        // toByteArray() returns big-endian two's complement, minimum length
        byte[] be = value.toByteArray();
        byte[] le = new byte[BYTES];

        // Determine fill byte for sign extension (0x00 for positive, 0xFF for negative)
        byte fill = (value.signum() < 0) ? (byte) 0xFF : (byte) 0x00;

        // First fill with sign extension
        Arrays.fill(le, fill);

        // Copy big-endian bytes reversed into little-endian buffer
        // be[0] is most significant; we copy to le[BYTES-1..] going down
        int srcLen = Math.min(be.length, BYTES);
        for (int i = 0; i < srcLen; i++) {
            le[i] = be[be.length - 1 - i];
        }

        out.writeBytes(le);
    }

    @Override
    public BigInteger read(ChInputStream in) throws IOException {
        byte[] le = in.readBytes(BYTES);
        // Convert little-endian to big-endian for BigInteger constructor
        byte[] be = new byte[BYTES];
        for (int i = 0; i < BYTES; i++) {
            be[i] = le[BYTES - 1 - i];
        }
        return new BigInteger(be); // two's complement, big-endian
    }
}

package io.blinkhouse.core.type.handler;

import io.blinkhouse.core.protocol.ChInputStream;
import io.blinkhouse.core.protocol.ChOutputStream;
import io.blinkhouse.core.type.TypeHandler;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;

/**
 * Handles ClickHouse {@code Decimal(38, scale)} ↔ Java {@link BigDecimal}.
 *
 * <p>Wire format: 16 bytes, little-endian two's complement integer representing
 * the unscaled value. The scale is fixed at construction time.
 *
 * <p>For Spike B the scale is 9, giving {@code Decimal(38,9)}.
 */
public final class Decimal128Handler implements TypeHandler<BigDecimal> {

    private static final int BYTES = 16;
    private final int scale;

    public Decimal128Handler(int scale) {
        this.scale = scale;
    }

    @Override
    public String clickHouseTypeName() {
        return "Decimal(38," + scale + ")";
    }

    @Override
    public void write(ChOutputStream out, BigDecimal value) throws IOException {
        BigInteger unscaled = value.setScale(scale).unscaledValue();
        byte[] be = unscaled.toByteArray();
        byte[] le = new byte[BYTES];

        byte fill = (unscaled.signum() < 0) ? (byte) 0xFF : (byte) 0x00;
        Arrays.fill(le, fill);

        int srcLen = Math.min(be.length, BYTES);
        for (int i = 0; i < srcLen; i++) {
            le[i] = be[be.length - 1 - i];
        }

        out.writeBytes(le);
    }

    @Override
    public BigDecimal read(ChInputStream in) throws IOException {
        byte[] le = in.readBytes(BYTES);
        byte[] be = new byte[BYTES];
        for (int i = 0; i < BYTES; i++) {
            be[i] = le[BYTES - 1 - i];
        }
        BigInteger unscaled = new BigInteger(be);
        return new BigDecimal(unscaled, scale);
    }
}

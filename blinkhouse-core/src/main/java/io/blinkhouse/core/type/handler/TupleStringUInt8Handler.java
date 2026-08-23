package io.blinkhouse.core.type.handler;

import io.blinkhouse.core.protocol.ChInputStream;
import io.blinkhouse.core.protocol.ChOutputStream;
import io.blinkhouse.core.type.TypeHandler;

import java.io.IOException;

/**
 * Handles ClickHouse {@code Tuple(String, UInt8)} ↔ Java {@link StringUInt8Tuple}.
 *
 * <p>Wire format: write the String (LEB128 length + UTF-8 bytes), then write
 * the UInt8 value as a single unsigned byte.
 */
public final class TupleStringUInt8Handler implements TypeHandler<TupleStringUInt8Handler.StringUInt8Tuple> {

    /**
     * Java representation of {@code Tuple(String, UInt8)}.
     *
     * @param s the string element
     * @param u the UInt8 element — stored as {@code short} to avoid sign-extension
     *          (UInt8 range is [0, 255] which overflows Java's signed {@code byte})
     */
    public record StringUInt8Tuple(String s, short u) {}

    @Override
    public String clickHouseTypeName() {
        return "Tuple(String, UInt8)";
    }

    @Override
    public void write(ChOutputStream out, StringUInt8Tuple value) throws IOException {
        out.writeString(value.s());
        out.writeByte(value.u() & 0xFF);
    }

    @Override
    public StringUInt8Tuple read(ChInputStream in) throws IOException {
        String s = in.readString();
        short u = (short)(in.readByte() & 0xFF);
        return new StringUInt8Tuple(s, u);
    }
}

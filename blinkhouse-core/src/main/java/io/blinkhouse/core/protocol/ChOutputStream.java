package io.blinkhouse.core.protocol;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Thin wrapper over {@link OutputStream} that writes ClickHouse RowBinary primitives.
 * All multi-byte integer types are written in little-endian byte order as required
 * by the RowBinary format specification.
 */
public final class ChOutputStream implements AutoCloseable {

    private final OutputStream out;

    public ChOutputStream(OutputStream out) {
        this.out = out;
    }

    public void writeByte(int b) throws IOException {
        out.write(b & 0xFF);
    }

    public void writeBytes(byte[] bytes) throws IOException {
        out.write(bytes);
    }

    public void writeShort(short value) throws IOException {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
    }

    public void writeInt(int value) throws IOException {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 24) & 0xFF);
    }

    public void writeLong(long value) throws IOException {
        out.write((int)(value & 0xFF));
        out.write((int)((value >> 8) & 0xFF));
        out.write((int)((value >> 16) & 0xFF));
        out.write((int)((value >> 24) & 0xFF));
        out.write((int)((value >> 32) & 0xFF));
        out.write((int)((value >> 40) & 0xFF));
        out.write((int)((value >> 48) & 0xFF));
        out.write((int)((value >> 56) & 0xFF));
    }

    public void writeFloat(float value) throws IOException {
        writeInt(Float.floatToRawIntBits(value));
    }

    public void writeDouble(double value) throws IOException {
        writeLong(Double.doubleToRawLongBits(value));
    }

    /**
     * Writes an unsigned LEB128 (variable-length) integer.
     * Used for string/array length prefixes in RowBinary format.
     */
    public void writeULeb128(long value) throws IOException {
        while (true) {
            int b = (int)(value & 0x7F);
            value >>>= 7;
            if (value != 0) {
                out.write(b | 0x80);
            } else {
                out.write(b);
                break;
            }
        }
    }

    /**
     * Writes a String as LEB128 length prefix followed by UTF-8 bytes.
     */
    public void writeString(String s) throws IOException {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        writeULeb128(bytes.length);
        out.write(bytes);
    }

    /**
     * Writes a fixed-length string of exactly {@code n} bytes.
     * Right-pads with null bytes if the string is shorter; truncates if longer.
     */
    public void writeFixedString(String s, int n) throws IOException {
        byte[] src = s.getBytes(StandardCharsets.UTF_8);
        byte[] buf = new byte[n];
        int len = Math.min(src.length, n);
        System.arraycopy(src, 0, buf, 0, len);
        // remaining bytes are already 0 (null padding)
        out.write(buf);
    }

    /**
     * Writes a boolean as a single byte: 0x00 for false, 0x01 for true.
     */
    public void writeBoolean(boolean b) throws IOException {
        out.write(b ? 1 : 0);
    }

    public void flush() throws IOException {
        out.flush();
    }

    @Override
    public void close() throws IOException {
        out.close();
    }
}

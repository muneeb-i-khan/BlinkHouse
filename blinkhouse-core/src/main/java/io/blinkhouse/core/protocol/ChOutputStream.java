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

    /** Wraps {@code out} for RowBinary encoding. */
    public ChOutputStream(OutputStream out) {
        this.out = out;
    }

    /** Writes the low 8 bits of {@code b}. */
    public void writeByte(int b) throws IOException {
        out.write(b & 0xFF);
    }

    /** Writes all bytes in {@code bytes}. */
    public void writeBytes(byte[] bytes) throws IOException {
        out.write(bytes);
    }

    /** Writes a little-endian {@code Int16} / {@code UInt16}. */
    public void writeShort(short value) throws IOException {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
    }

    /** Writes a little-endian {@code Int32} / {@code UInt32}. */
    public void writeInt(int value) throws IOException {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 24) & 0xFF);
    }

    /** Writes a little-endian {@code Int64} / {@code UInt64} / {@code DateTime64}. */
    public void writeLong(long value) throws IOException {
        out.write((int) (value & 0xFF));
        out.write((int) ((value >> 8) & 0xFF));
        out.write((int) ((value >> 16) & 0xFF));
        out.write((int) ((value >> 24) & 0xFF));
        out.write((int) ((value >> 32) & 0xFF));
        out.write((int) ((value >> 40) & 0xFF));
        out.write((int) ((value >> 48) & 0xFF));
        out.write((int) ((value >> 56) & 0xFF));
    }

    /** Writes a little-endian {@code Float32}. */
    public void writeFloat(float value) throws IOException {
        writeInt(Float.floatToRawIntBits(value));
    }

    /** Writes a little-endian {@code Float64}. */
    public void writeDouble(double value) throws IOException {
        writeLong(Double.doubleToRawLongBits(value));
    }

    /**
     * Writes an unsigned LEB128 (variable-length) integer.
     * Used for string and array length prefixes in the RowBinary format.
     */
    public void writeULeb128(long value) throws IOException {
        while (true) {
            int b = (int) (value & 0x7F);
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
     * Writes a {@code String} as a LEB128 length prefix followed by UTF-8 bytes.
     */
    public void writeString(String s) throws IOException {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        writeULeb128(bytes.length);
        out.write(bytes);
    }

    /**
     * Writes a {@code FixedString(n)}: exactly {@code n} bytes, right-padded with
     * null bytes if the string is shorter, truncated if longer.
     */
    public void writeFixedString(String s, int n) throws IOException {
        byte[] src = s.getBytes(StandardCharsets.UTF_8);
        byte[] buf = new byte[n];
        int len = Math.min(src.length, n);
        System.arraycopy(src, 0, buf, 0, len);
        out.write(buf);
    }

    /**
     * Writes a {@code Bool}: {@code 0x00} for false, {@code 0x01} for true.
     */
    public void writeBoolean(boolean b) throws IOException {
        out.write(b ? 1 : 0);
    }

    /** Flushes the underlying stream. */
    public void flush() throws IOException {
        out.flush();
    }

    @Override
    public void close() throws IOException {
        out.close();
    }
}

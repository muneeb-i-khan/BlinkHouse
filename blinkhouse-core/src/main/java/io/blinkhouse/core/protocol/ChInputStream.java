package io.blinkhouse.core.protocol;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Thin wrapper over {@link InputStream} that reads ClickHouse RowBinary primitives.
 * All multi-byte integer types are read in little-endian byte order as required
 * by the RowBinary format specification.
 */
public final class ChInputStream implements AutoCloseable {

    private final InputStream in;

    /** Wraps {@code in} for RowBinary decoding. */
    public ChInputStream(InputStream in) {
        this.in = in;
    }

    /** Reads one signed byte; throws {@link EOFException} on stream end. */
    public byte readByte() throws IOException {
        int b = in.read();
        if (b < 0) {
            throw new EOFException("Unexpected end of RowBinary stream");
        }
        return (byte) b;
    }

    /** Reads exactly {@code n} bytes; throws {@link EOFException} if the stream ends early. */
    public byte[] readBytes(int n) throws IOException {
        byte[] buf = new byte[n];
        int offset = 0;
        while (offset < n) {
            int read = in.read(buf, offset, n - offset);
            if (read < 0) {
                throw new EOFException(
                    "Unexpected end of RowBinary stream; needed " + n + " bytes, got " + offset);
            }
            offset += read;
        }
        return buf;
    }

    /** Reads a little-endian {@code Int16}. */
    public short readShort() throws IOException {
        int b0 = readUnsignedByte();
        int b1 = readUnsignedByte();
        return (short) (b0 | (b1 << 8));
    }

    /** Reads a little-endian {@code Int32} / {@code UInt32}. */
    public int readInt() throws IOException {
        int b0 = readUnsignedByte();
        int b1 = readUnsignedByte();
        int b2 = readUnsignedByte();
        int b3 = readUnsignedByte();
        return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
    }

    /** Reads a little-endian {@code Int64} / {@code UInt64} / {@code DateTime64}. */
    public long readLong() throws IOException {
        long b0 = readUnsignedByte();
        long b1 = readUnsignedByte();
        long b2 = readUnsignedByte();
        long b3 = readUnsignedByte();
        long b4 = readUnsignedByte();
        long b5 = readUnsignedByte();
        long b6 = readUnsignedByte();
        long b7 = readUnsignedByte();
        return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24)
             | (b4 << 32) | (b5 << 40) | (b6 << 48) | (b7 << 56);
    }

    /** Reads a little-endian {@code Float32}. */
    public float readFloat() throws IOException {
        return Float.intBitsToFloat(readInt());
    }

    /** Reads a little-endian {@code Float64}. */
    public double readDouble() throws IOException {
        return Double.longBitsToDouble(readLong());
    }

    /**
     * Reads an unsigned LEB128 (variable-length) integer.
     * Used for string and array length prefixes in the RowBinary format.
     */
    public long readULeb128() throws IOException {
        long result = 0;
        int shift = 0;
        while (true) {
            int b = readUnsignedByte();
            result |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                break;
            }
            shift += 7;
            if (shift >= 64) {
                throw new IOException("LEB128 varint too long");
            }
        }
        return result;
    }

    /**
     * Reads a {@code String}: LEB128 length prefix then UTF-8 bytes.
     */
    public String readString() throws IOException {
        int len = (int) readULeb128();
        byte[] bytes = readBytes(len);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Reads exactly {@code n} bytes as a {@code FixedString},
     * stripping trailing null bytes (ClickHouse null-pads fixed strings).
     */
    public String readFixedString(int n) throws IOException {
        byte[] bytes = readBytes(n);
        int len = n;
        while (len > 0 && bytes[len - 1] == 0) {
            len--;
        }
        return new String(bytes, 0, len, StandardCharsets.UTF_8);
    }

    /**
     * Reads a single byte and returns {@code true} if it is non-zero.
     */
    public boolean readBoolean() throws IOException {
        return readByte() != 0;
    }

    private int readUnsignedByte() throws IOException {
        int b = in.read();
        if (b < 0) {
            throw new EOFException("Unexpected end of RowBinary stream");
        }
        return b;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }
}

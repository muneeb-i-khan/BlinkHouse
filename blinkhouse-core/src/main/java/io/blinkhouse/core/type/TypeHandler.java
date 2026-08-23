package io.blinkhouse.core.type;

import io.blinkhouse.core.protocol.ChInputStream;
import io.blinkhouse.core.protocol.ChOutputStream;
import java.io.IOException;

/**
 * SPI for converting between a Java type {@code J} and the ClickHouse RowBinary
 * wire encoding for a specific ClickHouse column type.
 *
 * <p>Each implementation handles exactly one ClickHouse type. Implementations
 * must be stateless and thread-safe.
 *
 * @param <J> the Java type that maps to the ClickHouse column type
 */
public interface TypeHandler<J> {

    /**
     * Returns the canonical ClickHouse type name this handler targets,
     * e.g. {@code "UInt64"}, {@code "DateTime64(9,'Asia/Kolkata')"}, {@code "UUID"}.
     */
    String clickHouseTypeName();

    /**
     * Serialises {@code value} into ClickHouse RowBinary wire format.
     *
     * @param out   the output stream to write to
     * @param value the Java value to serialise; must not be {@code null} unless
     *              the handler explicitly documents nullable support
     */
    void write(ChOutputStream out, J value) throws IOException;

    /**
     * Deserialises one value from the ClickHouse RowBinary stream.
     *
     * @param in the input stream positioned at the start of the value
     * @return the deserialised Java value
     */
    J read(ChInputStream in) throws IOException;
}

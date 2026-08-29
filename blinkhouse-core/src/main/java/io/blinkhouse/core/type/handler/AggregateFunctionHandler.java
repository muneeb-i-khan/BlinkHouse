package io.blinkhouse.core.type.handler;

import io.blinkhouse.core.protocol.ChInputStream;
import io.blinkhouse.core.protocol.ChOutputStream;
import io.blinkhouse.core.type.TypeHandler;

import java.io.IOException;

/**
 * Type handler for ClickHouse {@code AggregateFunction(fn, ...)} columns.
 *
 * <p>Aggregate function state is stored by ClickHouse as an opaque binary blob
 * that can only be interpreted by ClickHouse's own aggregation machinery.
 * This handler treats the state as a {@code byte[]} — read-through only.
 *
 * <p>To query values from an {@code AggregateFunction} column, use the
 * {@code -Merge} combinator in SQL — see {@link io.blinkhouse.core.query.Functions}
 * for typed helpers such as {@code uniqMerge}, {@code sumMerge}, etc.
 *
 * <p><strong>Write semantics:</strong> writing raw aggregate state bytes is valid
 * only when copying state from one AggregatingMergeTree to another via
 * {@code INSERT INTO … SELECT … FROM …}. Inserting user-constructed bytes is
 * undefined behaviour in ClickHouse.
 */
public final class AggregateFunctionHandler implements TypeHandler<byte[]> {

    private final String typeName;

    /**
     * Creates a handler for a specific aggregate function type signature.
     *
     * @param typeName the full ClickHouse type name,
     *                 e.g. {@code "AggregateFunction(uniq, UInt64)"}
     */
    public AggregateFunctionHandler(String typeName) {
        this.typeName = typeName;
    }

    /** Returns a handler for the most common {@code AggregateFunction(uniq, UInt64)} type. */
    public static AggregateFunctionHandler forUniqUInt64() {
        return new AggregateFunctionHandler("AggregateFunction(uniq, UInt64)");
    }

    /** Returns a handler for {@code AggregateFunction(sum, Float64)}. */
    public static AggregateFunctionHandler forSumFloat64() {
        return new AggregateFunctionHandler("AggregateFunction(sum, Float64)");
    }

    @Override
    public String clickHouseTypeName() {
        return typeName;
    }

    /**
     * Reads the opaque aggregate state as a length-prefixed binary blob.
     *
     * <p>The wire format for aggregate state in RowBinary is a LEB128 length
     * prefix followed by the raw state bytes.
     */
    @Override
    public byte[] read(ChInputStream in) throws IOException {
        int len = (int) in.readULeb128();
        return in.readBytes(len);
    }

    /**
     * Writes the opaque aggregate state as a length-prefixed binary blob.
     *
     * @param out   the output stream
     * @param value the raw state bytes (must not be {@code null}; use {@code new byte[0]}
     *              for an empty/initial state)
     */
    @Override
    public void write(ChOutputStream out, byte[] value) throws IOException {
        byte[] bytes = value == null ? new byte[0] : value;
        out.writeULeb128(bytes.length);
        out.writeBytes(bytes);
    }
}

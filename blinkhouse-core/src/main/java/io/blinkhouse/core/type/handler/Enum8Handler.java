package io.blinkhouse.core.type.handler;

import io.blinkhouse.core.protocol.ChInputStream;
import io.blinkhouse.core.protocol.ChOutputStream;
import io.blinkhouse.core.type.TypeHandler;

import java.io.IOException;
import java.util.Map;

/**
 * Handles ClickHouse {@code Enum8('NAME' = value, ...)} ↔ Java {@link String} (enum name).
 *
 * <p>Wire format: 1 signed byte representing the numeric enum value.
 * The mapping between names and byte values is provided at construction time.
 *
 * <p>For Spike B the enum is: {@code Enum8('RED' = -1, 'GREEN' = 0, 'BLUE' = 1)}.
 */
public final class Enum8Handler implements TypeHandler<String> {

    private final Map<Byte, String> valueToName;
    private final Map<String, Byte> nameToValue;
    private final String clickHouseTypeName;

    /**
     * @param clickHouseTypeName the full ClickHouse type string, e.g.
     *                           {@code "Enum8('RED' = -1, 'GREEN' = 0, 'BLUE' = 1)"}
     * @param valueToName        mapping from wire byte value to enum name
     * @param nameToValue        mapping from enum name to wire byte value
     */
    public Enum8Handler(String clickHouseTypeName,
                        Map<Byte, String> valueToName,
                        Map<String, Byte> nameToValue) {
        this.clickHouseTypeName = clickHouseTypeName;
        this.valueToName = Map.copyOf(valueToName);
        this.nameToValue = Map.copyOf(nameToValue);
    }

    @Override
    public String clickHouseTypeName() {
        return clickHouseTypeName;
    }

    @Override
    public void write(ChOutputStream out, String value) throws IOException {
        Byte b = nameToValue.get(value);
        if (b == null) {
            throw new IOException("Unknown enum name: '" + value + "'");
        }
        out.writeByte(b);
    }

    @Override
    public String read(ChInputStream in) throws IOException {
        byte b = in.readByte();
        String name = valueToName.get(b);
        if (name == null) {
            throw new IOException("Unknown enum byte value: " + b);
        }
        return name;
    }
}

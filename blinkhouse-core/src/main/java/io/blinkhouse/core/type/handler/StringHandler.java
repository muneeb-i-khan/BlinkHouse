package io.blinkhouse.core.type.handler;

import io.blinkhouse.core.protocol.ChInputStream;
import io.blinkhouse.core.protocol.ChOutputStream;
import io.blinkhouse.core.type.TypeHandler;

import java.io.IOException;

/**
 * Handles ClickHouse {@code String} ↔ Java {@link String}.
 *
 * <p>Wire format: LEB128 length prefix followed by UTF-8 bytes.
 */
public final class StringHandler implements TypeHandler<String> {

    @Override
    public String clickHouseTypeName() {
        return "String";
    }

    @Override
    public void write(ChOutputStream out, String value) throws IOException {
        out.writeString(value == null ? "" : value);
    }

    @Override
    public String read(ChInputStream in) throws IOException {
        return in.readString();
    }
}

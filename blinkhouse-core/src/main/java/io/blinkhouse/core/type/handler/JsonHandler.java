package io.blinkhouse.core.type.handler;

import io.blinkhouse.core.protocol.ChInputStream;
import io.blinkhouse.core.protocol.ChOutputStream;
import io.blinkhouse.core.type.TypeHandler;

import java.io.IOException;

/**
 * Handles ClickHouse {@code JSON} ↔ Java {@link String} (raw JSON string).
 *
 * <p>Wire format: LEB128 length prefix followed by UTF-8 bytes — identical to
 * the plain {@code String} wire format in RowBinary.
 *
 * <p><strong>Version gate:</strong> The {@code JSON} type (distinct from
 * {@code JSONEachRow} format) is only available in ClickHouse 24.x and later.
 * In production, this handler should be registered only when the server version
 * is confirmed to be ≥ 24.0. The integration test checks the version and skips
 * if the server is older.
 *
 * <p>ClickHouse 24.x+ only; version-gated in production.
 */
public final class JsonHandler implements TypeHandler<String> {

    @Override
    public String clickHouseTypeName() {
        return "JSON";
    }

    @Override
    public void write(ChOutputStream out, String value) throws IOException {
        out.writeString(value);
    }

    @Override
    public String read(ChInputStream in) throws IOException {
        return in.readString();
    }
}

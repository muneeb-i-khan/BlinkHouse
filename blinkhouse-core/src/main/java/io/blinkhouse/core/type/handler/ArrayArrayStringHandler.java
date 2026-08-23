package io.blinkhouse.core.type.handler;

import io.blinkhouse.core.protocol.ChInputStream;
import io.blinkhouse.core.protocol.ChOutputStream;
import io.blinkhouse.core.type.TypeHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles ClickHouse {@code Array(Array(String))} ↔ Java {@code List<List<String>>}.
 *
 * <p>Wire format (RowBinary):
 * <ul>
 *   <li>ULeb128: outer array element count</li>
 *   <li>For each inner array:
 *     <ul>
 *       <li>ULeb128: inner array element count</li>
 *       <li>For each string: ULeb128 length + UTF-8 bytes</li>
 *     </ul>
 *   </li>
 * </ul>
 */
public final class ArrayArrayStringHandler implements TypeHandler<List<List<String>>> {

    @Override
    public String clickHouseTypeName() {
        return "Array(Array(String))";
    }

    @Override
    public void write(ChOutputStream out, List<List<String>> value) throws IOException {
        out.writeULeb128(value.size());
        for (List<String> inner : value) {
            out.writeULeb128(inner.size());
            for (String s : inner) {
                out.writeString(s);
            }
        }
    }

    @Override
    public List<List<String>> read(ChInputStream in) throws IOException {
        int outerSize = (int) in.readULeb128();
        List<List<String>> outer = new ArrayList<>(outerSize);
        for (int i = 0; i < outerSize; i++) {
            int innerSize = (int) in.readULeb128();
            List<String> inner = new ArrayList<>(innerSize);
            for (int j = 0; j < innerSize; j++) {
                inner.add(in.readString());
            }
            outer.add(inner);
        }
        return outer;
    }
}

package io.blinkhouse.core.type.handler;

import io.blinkhouse.core.protocol.ChInputStream;
import io.blinkhouse.core.protocol.ChOutputStream;
import io.blinkhouse.core.type.TypeHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles ClickHouse {@code Map(String, Array(UInt32))} ↔ Java {@code Map<String, List<Long>>}.
 *
 * <p>UInt32 values are widened to {@code long} to avoid sign-extension issues
 * (UInt32 max is 4,294,967,295 which overflows Java's {@code int}).
 *
 * <p>Wire format (RowBinary):
 * <ul>
 *   <li>ULeb128: number of map entries</li>
 *   <li>For each entry:
 *     <ul>
 *       <li>Key: ULeb128 length + UTF-8 bytes</li>
 *       <li>Value array: ULeb128 element count, then each UInt32 as 4 bytes LE</li>
 *     </ul>
 *   </li>
 * </ul>
 */
public final class MapStringArrayUInt32Handler implements TypeHandler<Map<String, List<Long>>> {

    @Override
    public String clickHouseTypeName() {
        return "Map(String, Array(UInt32))";
    }

    @Override
    public void write(ChOutputStream out, Map<String, List<Long>> value) throws IOException {
        out.writeULeb128(value.size());
        for (Map.Entry<String, List<Long>> entry : value.entrySet()) {
            out.writeString(entry.getKey());
            List<Long> arr = entry.getValue();
            out.writeULeb128(arr.size());
            for (long v : arr) {
                // Write as 4 bytes LE (UInt32)
                out.writeByte((int)(v & 0xFF));
                out.writeByte((int)((v >> 8) & 0xFF));
                out.writeByte((int)((v >> 16) & 0xFF));
                out.writeByte((int)((v >> 24) & 0xFF));
            }
        }
    }

    @Override
    public Map<String, List<Long>> read(ChInputStream in) throws IOException {
        int size = (int) in.readULeb128();
        Map<String, List<Long>> map = new LinkedHashMap<>(size * 2);
        for (int i = 0; i < size; i++) {
            String key = in.readString();
            int arrSize = (int) in.readULeb128();
            List<Long> arr = new ArrayList<>(arrSize);
            for (int j = 0; j < arrSize; j++) {
                // Read 4 bytes LE as UInt32 widened to long
                long b0 = in.readByte() & 0xFFL;
                long b1 = in.readByte() & 0xFFL;
                long b2 = in.readByte() & 0xFFL;
                long b3 = in.readByte() & 0xFFL;
                arr.add(b0 | (b1 << 8) | (b2 << 16) | (b3 << 24));
            }
            map.put(key, arr);
        }
        return map;
    }
}

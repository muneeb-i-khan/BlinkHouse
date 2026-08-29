package io.blinkhouse.core.type.handler;

import io.blinkhouse.core.protocol.ChInputStream;
import io.blinkhouse.core.protocol.ChOutputStream;
import io.blinkhouse.core.type.TypeHandler;
import io.blinkhouse.core.type.geo.GeoPoint;
import io.blinkhouse.core.type.geo.GeoRing;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Type handler for the ClickHouse {@code Ring} geo type.
 *
 * <p>ClickHouse encodes {@code Ring} as {@code Array(Point)}: a LEB128 element count
 * followed by that many {@code Tuple(Float64, Float64)} values.
 */
public final class GeoRingHandler implements TypeHandler<GeoRing> {

    @Override
    public String clickHouseTypeName() {
        return "Ring";
    }

    @Override
    public GeoRing read(ChInputStream in) throws IOException {
        int n = (int) in.readULeb128();
        List<GeoPoint> points = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            double lon = in.readDouble();
            double lat = in.readDouble();
            points.add(new GeoPoint(lon, lat));
        }
        return new GeoRing(points);
    }

    @Override
    public void write(ChOutputStream out, GeoRing value) throws IOException {
        if (value == null) {
            out.writeULeb128(0);
            return;
        }
        List<GeoPoint> pts = value.getPoints();
        out.writeULeb128(pts.size());
        for (GeoPoint p : pts) {
            out.writeDouble(p.getLongitude());
            out.writeDouble(p.getLatitude());
        }
    }
}

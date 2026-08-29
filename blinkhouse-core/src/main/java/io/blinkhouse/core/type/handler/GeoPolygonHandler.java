package io.blinkhouse.core.type.handler;

import io.blinkhouse.core.protocol.ChInputStream;
import io.blinkhouse.core.protocol.ChOutputStream;
import io.blinkhouse.core.type.TypeHandler;
import io.blinkhouse.core.type.geo.GeoPoint;
import io.blinkhouse.core.type.geo.GeoRing;
import io.blinkhouse.core.type.geo.GeoPolygon;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Type handler for the ClickHouse {@code Polygon} geo type.
 *
 * <p>ClickHouse encodes {@code Polygon} as {@code Array(Ring)}: a LEB128 ring count
 * followed by that many rings, where each ring is itself an {@code Array(Point)}.
 */
public final class GeoPolygonHandler implements TypeHandler<GeoPolygon> {

    @Override
    public String clickHouseTypeName() {
        return "Polygon";
    }

    @Override
    public GeoPolygon read(ChInputStream in) throws IOException {
        int ringCount = (int) in.readULeb128();
        List<GeoRing> rings = new ArrayList<>(ringCount);
        for (int r = 0; r < ringCount; r++) {
            int n = (int) in.readULeb128();
            List<GeoPoint> points = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                double lon = in.readDouble();
                double lat = in.readDouble();
                points.add(new GeoPoint(lon, lat));
            }
            rings.add(new GeoRing(points));
        }
        return new GeoPolygon(rings);
    }

    @Override
    public void write(ChOutputStream out, GeoPolygon value) throws IOException {
        if (value == null) {
            out.writeULeb128(0);
            return;
        }
        List<GeoRing> rings = value.getRings();
        out.writeULeb128(rings.size());
        for (GeoRing ring : rings) {
            List<GeoPoint> pts = ring.getPoints();
            out.writeULeb128(pts.size());
            for (GeoPoint p : pts) {
                out.writeDouble(p.getLongitude());
                out.writeDouble(p.getLatitude());
            }
        }
    }
}

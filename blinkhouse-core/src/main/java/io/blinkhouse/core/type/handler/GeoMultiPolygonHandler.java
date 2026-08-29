package io.blinkhouse.core.type.handler;

import io.blinkhouse.core.protocol.ChInputStream;
import io.blinkhouse.core.protocol.ChOutputStream;
import io.blinkhouse.core.type.TypeHandler;
import io.blinkhouse.core.type.geo.GeoMultiPolygon;
import io.blinkhouse.core.type.geo.GeoPoint;
import io.blinkhouse.core.type.geo.GeoPolygon;
import io.blinkhouse.core.type.geo.GeoRing;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Type handler for the ClickHouse {@code MultiPolygon} geo type.
 *
 * <p>ClickHouse encodes {@code MultiPolygon} as {@code Array(Polygon)}: a LEB128
 * polygon count, then each polygon as {@code Array(Ring)}, each ring as {@code Array(Point)}.
 */
public final class GeoMultiPolygonHandler implements TypeHandler<GeoMultiPolygon> {

    @Override
    public String clickHouseTypeName() {
        return "MultiPolygon";
    }

    @Override
    public GeoMultiPolygon read(ChInputStream in) throws IOException {
        int polyCount = (int) in.readULeb128();
        List<GeoPolygon> polygons = new ArrayList<>(polyCount);
        for (int p = 0; p < polyCount; p++) {
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
            polygons.add(new GeoPolygon(rings));
        }
        return new GeoMultiPolygon(polygons);
    }

    @Override
    public void write(ChOutputStream out, GeoMultiPolygon value) throws IOException {
        if (value == null) {
            out.writeULeb128(0);
            return;
        }
        List<GeoPolygon> polygons = value.getPolygons();
        out.writeULeb128(polygons.size());
        for (GeoPolygon poly : polygons) {
            List<GeoRing> rings = poly.getRings();
            out.writeULeb128(rings.size());
            for (GeoRing ring : rings) {
                List<GeoPoint> pts = ring.getPoints();
                out.writeULeb128(pts.size());
                for (GeoPoint pt : pts) {
                    out.writeDouble(pt.getLongitude());
                    out.writeDouble(pt.getLatitude());
                }
            }
        }
    }
}

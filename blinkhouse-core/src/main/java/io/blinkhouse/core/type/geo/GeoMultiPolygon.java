package io.blinkhouse.core.type.geo;

import java.util.List;

/**
 * Java representation of the ClickHouse {@code MultiPolygon} geo type.
 *
 * <p>ClickHouse encodes {@code MultiPolygon} as {@code Array(Polygon)}.
 */
public final class GeoMultiPolygon {

    private final List<GeoPolygon> polygons;

    public GeoMultiPolygon(List<GeoPolygon> polygons) {
        if (polygons == null || polygons.isEmpty()) {
            throw new IllegalArgumentException("GeoMultiPolygon requires at least one polygon");
        }
        this.polygons = List.copyOf(polygons);
    }

    /** The polygons in this multi-polygon geometry. */
    public List<GeoPolygon> getPolygons() {
        return polygons;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GeoMultiPolygon)) return false;
        return polygons.equals(((GeoMultiPolygon) o).polygons);
    }

    @Override
    public int hashCode() {
        return polygons.hashCode();
    }

    @Override
    public String toString() {
        return "GeoMultiPolygon(" + polygons.size() + " polygons)";
    }
}

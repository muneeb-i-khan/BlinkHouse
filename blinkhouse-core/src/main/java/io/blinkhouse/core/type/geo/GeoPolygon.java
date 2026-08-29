package io.blinkhouse.core.type.geo;

import java.util.List;

/**
 * Java representation of the ClickHouse {@code Polygon} geo type.
 *
 * <p>A polygon is a list of rings: the first ring is the outer boundary,
 * subsequent rings are holes. ClickHouse encodes {@code Polygon} as {@code Array(Ring)}.
 */
public final class GeoPolygon {

    private final List<GeoRing> rings;

    public GeoPolygon(List<GeoRing> rings) {
        if (rings == null || rings.isEmpty()) {
            throw new IllegalArgumentException("GeoPolygon requires at least one ring");
        }
        this.rings = List.copyOf(rings);
    }

    /** Outer ring first, then any hole rings. */
    public List<GeoRing> getRings() {
        return rings;
    }

    /** The outer boundary ring. */
    public GeoRing getOuterRing() {
        return rings.get(0);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GeoPolygon)) return false;
        return rings.equals(((GeoPolygon) o).rings);
    }

    @Override
    public int hashCode() {
        return rings.hashCode();
    }

    @Override
    public String toString() {
        return "GeoPolygon(" + rings.size() + " rings)";
    }
}

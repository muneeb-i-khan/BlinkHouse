package io.blinkhouse.core.type.geo;

import java.util.List;

/**
 * Java representation of the ClickHouse {@code Ring} geo type.
 *
 * <p>A ring is an ordered list of {@link GeoPoint}s forming a closed polygon boundary.
 * ClickHouse encodes {@code Ring} as {@code Array(Point)}.
 */
public final class GeoRing {

    private final List<GeoPoint> points;

    public GeoRing(List<GeoPoint> points) {
        if (points == null || points.isEmpty()) {
            throw new IllegalArgumentException("GeoRing requires at least one point");
        }
        this.points = List.copyOf(points);
    }

    /** The ordered list of points forming this ring. */
    public List<GeoPoint> getPoints() {
        return points;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GeoRing)) return false;
        return points.equals(((GeoRing) o).points);
    }

    @Override
    public int hashCode() {
        return points.hashCode();
    }

    @Override
    public String toString() {
        return "GeoRing(" + points.size() + " points)";
    }
}

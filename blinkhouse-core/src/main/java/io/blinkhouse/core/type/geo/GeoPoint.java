package io.blinkhouse.core.type.geo;

/**
 * Java representation of the ClickHouse {@code Point} geo type.
 *
 * <p>ClickHouse encodes {@code Point} as {@code Tuple(Float64, Float64)}: (longitude, latitude).
 * Immutable and safe to use as a Map key.
 */
public final class GeoPoint {

    private final double longitude;
    private final double latitude;

    public GeoPoint(double longitude, double latitude) {
        this.longitude = longitude;
        this.latitude = latitude;
    }

    /** Longitude (X coordinate). */
    public double getLongitude() {
        return longitude;
    }

    /** Latitude (Y coordinate). */
    public double getLatitude() {
        return latitude;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GeoPoint)) return false;
        GeoPoint that = (GeoPoint) o;
        return Double.compare(longitude, that.longitude) == 0
            && Double.compare(latitude, that.latitude) == 0;
    }

    @Override
    public int hashCode() {
        long h = Double.doubleToLongBits(longitude) * 31 + Double.doubleToLongBits(latitude);
        return (int) (h ^ (h >>> 32));
    }

    @Override
    public String toString() {
        return "GeoPoint(" + longitude + ", " + latitude + ")";
    }
}

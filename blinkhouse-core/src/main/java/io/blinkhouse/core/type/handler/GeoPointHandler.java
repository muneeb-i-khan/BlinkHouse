package io.blinkhouse.core.type.handler;

import io.blinkhouse.core.protocol.ChInputStream;
import io.blinkhouse.core.protocol.ChOutputStream;
import io.blinkhouse.core.type.TypeHandler;
import io.blinkhouse.core.type.geo.GeoPoint;

import java.io.IOException;

/**
 * Type handler for the ClickHouse {@code Point} geo type.
 *
 * <p>ClickHouse encodes {@code Point} as {@code Tuple(Float64, Float64)}:
 * longitude first, latitude second. Both values are little-endian Float64.
 */
public final class GeoPointHandler implements TypeHandler<GeoPoint> {

    @Override
    public String clickHouseTypeName() {
        return "Point";
    }

    @Override
    public GeoPoint read(ChInputStream in) throws IOException {
        double lon = in.readDouble();
        double lat = in.readDouble();
        return new GeoPoint(lon, lat);
    }

    @Override
    public void write(ChOutputStream out, GeoPoint value) throws IOException {
        if (value == null) {
            out.writeDouble(0.0);
            out.writeDouble(0.0);
        } else {
            out.writeDouble(value.getLongitude());
            out.writeDouble(value.getLatitude());
        }
    }
}

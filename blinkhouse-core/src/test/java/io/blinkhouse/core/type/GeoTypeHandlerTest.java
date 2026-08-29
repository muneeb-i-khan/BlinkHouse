package io.blinkhouse.core.type;

import io.blinkhouse.core.protocol.ChInputStream;
import io.blinkhouse.core.protocol.ChOutputStream;
import io.blinkhouse.core.type.geo.GeoMultiPolygon;
import io.blinkhouse.core.type.geo.GeoPoint;
import io.blinkhouse.core.type.geo.GeoPolygon;
import io.blinkhouse.core.type.geo.GeoRing;
import io.blinkhouse.core.type.handler.GeoMultiPolygonHandler;
import io.blinkhouse.core.type.handler.GeoPointHandler;
import io.blinkhouse.core.type.handler.GeoPolygonHandler;
import io.blinkhouse.core.type.handler.GeoRingHandler;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Round-trip tests for geo type handlers.
 */
class GeoTypeHandlerTest {

    // ── GeoPoint ──────────────────────────────────────────────────────────────

    @Test
    void pointRoundTrip() throws IOException {
        GeoPointHandler h = new GeoPointHandler();
        assertThat(h.clickHouseTypeName()).isEqualTo("Point");

        GeoPoint pt = new GeoPoint(77.1234, 28.5678);
        byte[] bytes = write(h, pt);
        GeoPoint out = read(h, bytes);

        assertThat(out.getLongitude()).isCloseTo(77.1234, within(1e-9));
        assertThat(out.getLatitude()).isCloseTo(28.5678, within(1e-9));
    }

    @Test
    void pointOrigin() throws IOException {
        GeoPointHandler h = new GeoPointHandler();
        GeoPoint origin = new GeoPoint(0.0, 0.0);
        byte[] bytes = write(h, origin);
        GeoPoint out = read(h, bytes);

        assertThat(out).isEqualTo(origin);
    }

    // ── GeoRing ──────────────────────────────────────────────────────────────

    @Test
    void ringRoundTrip() throws IOException {
        GeoRingHandler h = new GeoRingHandler();
        assertThat(h.clickHouseTypeName()).isEqualTo("Ring");

        GeoRing ring = new GeoRing(List.of(
            new GeoPoint(0.0, 0.0),
            new GeoPoint(1.0, 0.0),
            new GeoPoint(1.0, 1.0),
            new GeoPoint(0.0, 0.0)
        ));
        byte[] bytes = write(h, ring);
        GeoRing out = read(h, bytes);

        assertThat(out.getPoints()).hasSize(4);
        assertThat(out.getPoints().get(1).getLongitude()).isEqualTo(1.0);
    }

    @Test
    void ringNullWritesEmptyArray() throws IOException {
        GeoRingHandler h = new GeoRingHandler();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        h.write(new ChOutputStream(bos), null);
        assertThat(bos.size()).isGreaterThan(0);
    }

    // ── GeoPolygon ────────────────────────────────────────────────────────────

    @Test
    void polygonRoundTrip() throws IOException {
        GeoPolygonHandler h = new GeoPolygonHandler();
        assertThat(h.clickHouseTypeName()).isEqualTo("Polygon");

        GeoRing outer = new GeoRing(List.of(
            new GeoPoint(0.0, 0.0),
            new GeoPoint(10.0, 0.0),
            new GeoPoint(10.0, 10.0),
            new GeoPoint(0.0, 0.0)
        ));
        GeoPolygon poly = new GeoPolygon(List.of(outer));
        byte[] bytes = write(h, poly);
        GeoPolygon out = read(h, bytes);

        assertThat(out.getRings()).hasSize(1);
        assertThat(out.getOuterRing().getPoints()).hasSize(4);
    }

    @Test
    void polygonWithHole() throws IOException {
        GeoPolygonHandler h = new GeoPolygonHandler();
        GeoRing outer = new GeoRing(List.of(
            new GeoPoint(0.0, 0.0),
            new GeoPoint(10.0, 0.0),
            new GeoPoint(0.0, 0.0)
        ));
        GeoRing hole = new GeoRing(List.of(
            new GeoPoint(2.0, 2.0),
            new GeoPoint(4.0, 2.0),
            new GeoPoint(2.0, 2.0)
        ));
        GeoPolygon poly = new GeoPolygon(List.of(outer, hole));
        byte[] bytes = write(h, poly);
        GeoPolygon out = read(h, bytes);

        assertThat(out.getRings()).hasSize(2);
    }

    // ── GeoMultiPolygon ───────────────────────────────────────────────────────

    @Test
    void multiPolygonRoundTrip() throws IOException {
        GeoMultiPolygonHandler h = new GeoMultiPolygonHandler();
        assertThat(h.clickHouseTypeName()).isEqualTo("MultiPolygon");

        GeoRing ring1 = new GeoRing(List.of(
            new GeoPoint(0.0, 0.0), new GeoPoint(1.0, 0.0), new GeoPoint(0.0, 0.0)));
        GeoRing ring2 = new GeoRing(List.of(
            new GeoPoint(5.0, 5.0), new GeoPoint(6.0, 5.0), new GeoPoint(5.0, 5.0)));
        GeoMultiPolygon mp = new GeoMultiPolygon(List.of(
            new GeoPolygon(List.of(ring1)),
            new GeoPolygon(List.of(ring2))
        ));

        byte[] bytes = write(h, mp);
        GeoMultiPolygon out = read(h, bytes);

        assertThat(out.getPolygons()).hasSize(2);
        assertThat(out.getPolygons().get(0).getOuterRing().getPoints()).hasSize(3);
    }

    // ── GeoPoint equality / hashCode ──────────────────────────────────────────

    @Test
    void pointEquality() {
        assertThat(new GeoPoint(1.0, 2.0)).isEqualTo(new GeoPoint(1.0, 2.0));
        assertThat(new GeoPoint(1.0, 2.0)).isNotEqualTo(new GeoPoint(2.0, 1.0));
        assertThat(new GeoPoint(1.0, 2.0).hashCode())
            .isEqualTo(new GeoPoint(1.0, 2.0).hashCode());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private <T> byte[] write(TypeHandler<T> h, T value) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        h.write(new ChOutputStream(bos), value);
        return bos.toByteArray();
    }

    private <T> T read(TypeHandler<T> h, byte[] bytes) throws IOException {
        return h.read(new ChInputStream(new ByteArrayInputStream(bytes)));
    }
}

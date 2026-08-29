package io.blinkhouse.core.type;

import io.blinkhouse.core.protocol.ChInputStream;
import io.blinkhouse.core.protocol.ChOutputStream;
import io.blinkhouse.core.type.handler.AggregateFunctionHandler;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the AggregateFunctionHandler.
 */
class AggregateFunctionHandlerTest {

    @Test
    void typeNameReturned() {
        AggregateFunctionHandler h = new AggregateFunctionHandler("AggregateFunction(uniq, UInt64)");
        assertThat(h.clickHouseTypeName()).isEqualTo("AggregateFunction(uniq, UInt64)");
    }

    @Test
    void factoryForUniqUInt64() {
        AggregateFunctionHandler h = AggregateFunctionHandler.forUniqUInt64();
        assertThat(h.clickHouseTypeName()).isEqualTo("AggregateFunction(uniq, UInt64)");
    }

    @Test
    void factoryForSumFloat64() {
        AggregateFunctionHandler h = AggregateFunctionHandler.forSumFloat64();
        assertThat(h.clickHouseTypeName()).isEqualTo("AggregateFunction(sum, Float64)");
    }

    @Test
    void roundTripNonEmptyState() throws IOException {
        AggregateFunctionHandler h = AggregateFunctionHandler.forUniqUInt64();
        byte[] state = new byte[]{0x01, 0x02, 0x03, 0x04, 0x0A, 0x0B};

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        h.write(new ChOutputStream(bos), state);

        byte[] wire = bos.toByteArray();
        byte[] out = h.read(new ChInputStream(new ByteArrayInputStream(wire)));

        assertThat(out).containsExactly(state);
    }

    @Test
    void roundTripEmptyState() throws IOException {
        AggregateFunctionHandler h = AggregateFunctionHandler.forUniqUInt64();
        byte[] state = new byte[0];

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        h.write(new ChOutputStream(bos), state);
        byte[] out = h.read(new ChInputStream(new ByteArrayInputStream(bos.toByteArray())));

        assertThat(out).isEmpty();
    }

    @Test
    void writeNullTreatedAsEmpty() throws IOException {
        AggregateFunctionHandler h = AggregateFunctionHandler.forUniqUInt64();

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        h.write(new ChOutputStream(bos), null);
        byte[] out = h.read(new ChInputStream(new ByteArrayInputStream(bos.toByteArray())));

        assertThat(out).isEmpty();
    }

    @Test
    void largeStateRoundTrip() throws IOException {
        AggregateFunctionHandler h = AggregateFunctionHandler.forUniqUInt64();
        byte[] state = new byte[1024];
        for (int i = 0; i < state.length; i++) {
            state[i] = (byte) (i & 0xFF);
        }

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        h.write(new ChOutputStream(bos), state);
        byte[] out = h.read(new ChInputStream(new ByteArrayInputStream(bos.toByteArray())));

        assertThat(out).containsExactly(state);
    }
}

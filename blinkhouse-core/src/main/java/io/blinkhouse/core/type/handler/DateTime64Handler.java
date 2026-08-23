package io.blinkhouse.core.type.handler;

import io.blinkhouse.core.protocol.ChInputStream;
import io.blinkhouse.core.protocol.ChOutputStream;
import io.blinkhouse.core.type.TypeHandler;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Handles ClickHouse {@code DateTime64(precision, timezone)} ↔ Java {@link Instant}.
 *
 * <p>Wire format: 8 bytes, little-endian signed int64 representing ticks since
 * the Unix epoch at the given precision (e.g. precision=9 means nanoseconds,
 * precision=3 means milliseconds).
 *
 * <p><strong>CRITICAL:</strong> The {@link ZoneId} is taken from the constructor
 * and is stored in the column definition. The wire value is always epoch-relative
 * ticks regardless of timezone — the timezone is metadata for display only.
 * We NEVER fall back to {@link ZoneId#systemDefault()}.
 */
public final class DateTime64Handler implements TypeHandler<Instant> {

    private final int precision;
    private final ZoneId zoneId;
    private final long ticksPerSecond;

    /**
     * @param precision the sub-second precision (0=seconds, 3=millis, 6=micros, 9=nanos)
     * @param zoneId    the timezone embedded in the ClickHouse column definition;
     *                  MUST match the column's timezone exactly
     */
    public DateTime64Handler(int precision, ZoneId zoneId) {
        if (precision < 0 || precision > 9) {
            throw new IllegalArgumentException("DateTime64 precision must be 0-9, got: " + precision);
        }
        this.precision = precision;
        this.zoneId = zoneId;
        this.ticksPerSecond = pow10(precision);
    }

    @Override
    public String clickHouseTypeName() {
        return "DateTime64(" + precision + ",'" + zoneId.getId() + "')";
    }

    @Override
    public void write(ChOutputStream out, Instant value) throws IOException {
        long ticks = toTicks(value);
        out.writeLong(ticks);
    }

    @Override
    public Instant read(ChInputStream in) throws IOException {
        long ticks = in.readLong();
        return fromTicks(ticks);
    }

    private long toTicks(Instant instant) {
        long epochSeconds = instant.getEpochSecond();
        int nano = instant.getNano();

        if (precision == 0) {
            return epochSeconds;
        } else if (precision <= 9) {
            // Convert nanoseconds to the required sub-second unit
            long subSecondTicks = nano / (1_000_000_000L / ticksPerSecond);
            return epochSeconds * ticksPerSecond + subSecondTicks;
        }
        return epochSeconds;
    }

    private Instant fromTicks(long ticks) {
        if (precision == 0) {
            return Instant.ofEpochSecond(ticks);
        }
        long epochSeconds = ticks / ticksPerSecond;
        long remainder = ticks % ticksPerSecond;
        if (remainder < 0) {
            epochSeconds--;
            remainder += ticksPerSecond;
        }
        long nanos = remainder * (1_000_000_000L / ticksPerSecond);
        return Instant.ofEpochSecond(epochSeconds, nanos);
    }

    private static long pow10(int exp) {
        long result = 1;
        for (int i = 0; i < exp; i++) result *= 10;
        return result;
    }
}

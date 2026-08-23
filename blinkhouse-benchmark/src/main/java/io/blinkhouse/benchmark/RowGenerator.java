package io.blinkhouse.benchmark;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Generates rows deterministically from a fixed seed so that every benchmark
 * run exercises exactly the same data — making results comparable across runs
 * and across the three transport paths.
 */
public final class RowGenerator {

    /** "2024-01-01T00:00:00Z" as epoch-milliseconds — base timestamp for generated rows. */
    private static final long BASE_EPOCH_MILLIS = Instant.parse("2024-01-01T00:00:00Z").toEpochMilli();

    private static final String[] COUNTRIES = {"IN", "US", "DE", "BR", "JP"};

    private RowGenerator() {}

    /**
     * Generates {@code count} rows deterministically.
     *
     * <ul>
     *   <li>{@code tenantId}   = {@code i % 100}
     *   <li>{@code tsMillis}   = {@code BASE_EPOCH_MILLIS + i * 1_000L}  (1 second apart)
     *   <li>{@code userId}     = {@code new UUID(i, i)}
     *   <li>{@code country}    = {@code COUNTRIES[i % 5]}
     *   <li>{@code durationMs} = {@code i % 5_000}
     * </ul>
     */
    public static List<TransportBenchmarkRow> generate(int count) {
        List<TransportBenchmarkRow> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rows.add(new TransportBenchmarkRow(
                    i % 100,
                    BASE_EPOCH_MILLIS + (long) i * 1_000L,
                    new UUID(i, i),
                    COUNTRIES[i % 5],
                    i % 5_000
            ));
        }
        return rows;
    }
}

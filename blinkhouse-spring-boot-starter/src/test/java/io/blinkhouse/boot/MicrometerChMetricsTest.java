package io.blinkhouse.boot;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MicrometerChMetrics} using an in-memory {@link SimpleMeterRegistry}.
 */
class MicrometerChMetricsTest {

    private MeterRegistry registry;
    private MicrometerChMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new MicrometerChMetrics(registry);
    }

    @Test
    void recordQueryRegistersTimer() {
        metrics.recordQuery("events", "select", "EventRepo", "findByTs", "success", 42L);

        Timer timer = registry.find("blinkhouse.query.duration")
                .tags("table", "events", "operation", "select",
                      "repository", "EventRepo", "method", "findByTs",
                      "outcome", "success")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isGreaterThanOrEqualTo(42.0);
    }

    @Test
    void recordQueryDistinguishesByOutcome() {
        metrics.recordQuery("events", "select", "none", "queryForList", "success", 10L);
        metrics.recordQuery("events", "select", "none", "queryForList", "error", 5L);

        Timer successTimer = registry.find("blinkhouse.query.duration")
                .tags("outcome", "success").timer();
        Timer errorTimer = registry.find("blinkhouse.query.duration")
                .tags("outcome", "error").timer();

        assertThat(successTimer).isNotNull();
        assertThat(errorTimer).isNotNull();
        assertThat(successTimer.count()).isEqualTo(1);
        assertThat(errorTimer.count()).isEqualTo(1);
    }

    @Test
    void recordBatchRegistersTimerAndCounters() {
        metrics.recordBatch("events", 5000L, 327680L, "success", 200L);

        Timer timer = registry.find("blinkhouse.batch.duration")
                .tags("table", "events", "outcome", "success")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);

        Counter rowCounter = registry.find("blinkhouse.batch.rows")
                .tags("table", "events", "outcome", "success")
                .counter();
        assertThat(rowCounter).isNotNull();
        assertThat(rowCounter.count()).isEqualTo(5000.0);

        Counter byteCounter = registry.find("blinkhouse.batch.bytes")
                .tags("table", "events", "outcome", "success")
                .counter();
        assertThat(byteCounter).isNotNull();
        assertThat(byteCounter.count()).isEqualTo(327680.0);
    }

    @Test
    void recordDeadLetterRegistersCounter() {
        metrics.recordDeadLetter("events", 10L);
        metrics.recordDeadLetter("events", 5L);

        Counter counter = registry.find("blinkhouse.insert.dead_letter.rows")
                .tags("table", "events")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(15.0);
    }

    @Test
    void recordBufferOccupancyRegistersGauges() {
        metrics.recordBufferOccupancy("events", 800L, 51200L);

        assertThat(registry.find("blinkhouse.buffer.rows").tags("table", "events").gauge())
                .isNotNull();
        assertThat(registry.find("blinkhouse.buffer.rows").tags("table", "events")
                .gauge().value()).isEqualTo(800.0);

        assertThat(registry.find("blinkhouse.buffer.bytes").tags("table", "events").gauge())
                .isNotNull();
        assertThat(registry.find("blinkhouse.buffer.bytes").tags("table", "events")
                .gauge().value()).isEqualTo(51200.0);
    }

    @Test
    void recordBufferOccupancyUpdatesGaugeInPlace() {
        metrics.recordBufferOccupancy("events", 800L, 51200L);
        metrics.recordBufferOccupancy("events", 400L, 25600L);

        assertThat(registry.find("blinkhouse.buffer.rows").tags("table", "events")
                .gauge().value()).isEqualTo(400.0);
    }

    @Test
    void recordSingleRowInsertRegistersCounter() {
        metrics.recordSingleRowInsert("orders");
        metrics.recordSingleRowInsert("orders");

        Counter counter = registry.find("blinkhouse.insert.singlerow")
                .tags("table", "orders")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(2.0);
    }

    @Test
    void multipleTablesDontCrossContaminate() {
        metrics.recordDeadLetter("orders", 3L);
        metrics.recordDeadLetter("events", 7L);

        assertThat(registry.find("blinkhouse.insert.dead_letter.rows")
                .tags("table", "orders").counter().count()).isEqualTo(3.0);
        assertThat(registry.find("blinkhouse.insert.dead_letter.rows")
                .tags("table", "events").counter().count()).isEqualTo(7.0);
    }
}

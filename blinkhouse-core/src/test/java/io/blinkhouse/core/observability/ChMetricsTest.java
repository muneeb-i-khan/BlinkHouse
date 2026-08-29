package io.blinkhouse.core.observability;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link ChMetrics} SPI contract and no-op implementation.
 */
class ChMetricsTest {

    /**
     * No-op implementation must be callable without throwing.
     */
    @Test
    void noopImplementationIsCallableForAllMethods() {
        ChMetrics noop = NoopChMetrics.INSTANCE;
        noop.recordQuery("tbl", "select", "repo", "findAll", "success", 42L);
        noop.recordBatch("tbl", 1000L, 65536L, "success", 120L);
        noop.recordDeadLetter("tbl", 5L);
        noop.recordBufferOccupancy("tbl", 800L, 51200L);
        noop.recordSingleRowInsert("tbl");
    }

    /**
     * A capturing stub verifies that the right arguments flow through the SPI.
     */
    @Test
    void capturingStubReceivesCorrectArguments() {
        CapturingChMetrics stub = new CapturingChMetrics();

        stub.recordQuery("events", "select", "EventRepo", "findByTs", "success", 15L);
        stub.recordBatch("events", 5000L, 327680L, "success", 200L);
        stub.recordDeadLetter("events", 10L);
        stub.recordBufferOccupancy("events", 4500L, 290000L);
        stub.recordSingleRowInsert("events");

        assertThat(stub.queryRecords).hasSize(1);
        assertThat(stub.queryRecords.get(0)).containsExactly(
                "events", "select", "EventRepo", "findByTs", "success", "15");

        assertThat(stub.batchRecords).hasSize(1);
        assertThat(stub.batchRecords.get(0)).containsExactly(
                "events", "5000", "327680", "success", "200");

        assertThat(stub.deadLetterRecords).hasSize(1);
        assertThat(stub.deadLetterRecords.get(0)).containsExactly("events", "10");

        assertThat(stub.bufferRecords).hasSize(1);
        assertThat(stub.bufferRecords.get(0)).containsExactly("events", "4500", "290000");

        assertThat(stub.singleRowRecords).containsExactly("events");
    }

    @Test
    void queryIdGeneratorProducesUniqueIds() {
        QueryIdGenerator gen = new QueryIdGenerator("myapp");
        String id1 = gen.generate();
        String id2 = gen.generate();

        assertThat(id1).startsWith("blinkhouse-myapp-");
        assertThat(id2).startsWith("blinkhouse-myapp-");
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    void queryIdGeneratorSanitisesAppName() {
        QueryIdGenerator gen = new QueryIdGenerator("my app/v2");
        assertThat(gen.generate()).startsWith("blinkhouse-my_app_v2-");
    }

    @Test
    void queryIdGeneratorDefaultsToApp() {
        QueryIdGenerator gen = new QueryIdGenerator(null);
        assertThat(gen.generate()).startsWith("blinkhouse-app-");
    }

    @Test
    void noopTracerReturnsNonNullSpanHandle() {
        NoopChTracer tracer = NoopChTracer.INSTANCE;
        Object span = tracer.startSpan("ch.select", "SELECT 1", "qid-1");
        assertThat(span).isNotNull();
        tracer.endSpan(span, null);
        tracer.endSpan(span, new RuntimeException("boom"));
    }

    // ── stub ──────────────────────────────────────────────────────────────────

    private static final class CapturingChMetrics implements ChMetrics {

        final List<String[]> queryRecords = new ArrayList<>();
        final List<String[]> batchRecords = new ArrayList<>();
        final List<String[]> deadLetterRecords = new ArrayList<>();
        final List<String[]> bufferRecords = new ArrayList<>();
        final List<String> singleRowRecords = new ArrayList<>();

        @Override
        public void recordQuery(String table, String operation, String repository,
                                String method, String outcome, long durationMs) {
            queryRecords.add(new String[]{table, operation, repository, method, outcome,
                    String.valueOf(durationMs)});
        }

        @Override
        public void recordBatch(String table, long rows, long bytes,
                                String outcome, long durationMs) {
            batchRecords.add(new String[]{table, String.valueOf(rows), String.valueOf(bytes),
                    outcome, String.valueOf(durationMs)});
        }

        @Override
        public void recordDeadLetter(String table, long rows) {
            deadLetterRecords.add(new String[]{table, String.valueOf(rows)});
        }

        @Override
        public void recordBufferOccupancy(String table, long buffRows, long buffBytes) {
            bufferRecords.add(new String[]{table, String.valueOf(buffRows),
                    String.valueOf(buffBytes)});
        }

        @Override
        public void recordSingleRowInsert(String table) {
            singleRowRecords.add(table);
        }
    }
}

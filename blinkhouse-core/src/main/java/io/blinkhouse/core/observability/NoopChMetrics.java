package io.blinkhouse.core.observability;

/**
 * No-op implementation of {@link ChMetrics}.
 *
 * <p>Used as the default when no {@link ChMetrics} bean is provided,
 * keeping {@code blinkhouse-core} free of any Micrometer dependency.
 */
public final class NoopChMetrics implements ChMetrics {

    /** Singleton instance. */
    public static final NoopChMetrics INSTANCE = new NoopChMetrics();

    private NoopChMetrics() {
    }

    @Override
    public void recordQuery(String table, String operation, String repository,
                            String method, String outcome, long durationMs) {
    }

    @Override
    public void recordBatch(String table, long rows, long bytes,
                            String outcome, long durationMs) {
    }

    @Override
    public void recordDeadLetter(String table, long rows) {
    }

    @Override
    public void recordBufferOccupancy(String table, long buffRows, long buffBytes) {
    }

    @Override
    public void recordSingleRowInsert(String table) {
    }
}

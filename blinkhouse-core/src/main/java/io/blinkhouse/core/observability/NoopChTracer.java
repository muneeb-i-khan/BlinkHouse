package io.blinkhouse.core.observability;

/**
 * No-op implementation of {@link ChTracer}.
 *
 * <p>Used as the default when no tracing backend is present,
 * keeping {@code blinkhouse-core} free of any tracing library dependency.
 */
public final class NoopChTracer implements ChTracer {

    /** Singleton no-op span handle returned by {@link #startSpan}. */
    private static final Object NOOP_SPAN = new Object();

    /** Singleton instance. */
    public static final NoopChTracer INSTANCE = new NoopChTracer();

    private NoopChTracer() {
    }

    @Override
    public Object startSpan(String operationName, String parameterisedSql, String queryId) {
        return NOOP_SPAN;
    }

    @Override
    public void endSpan(Object spanHandle, Throwable error) {
    }
}

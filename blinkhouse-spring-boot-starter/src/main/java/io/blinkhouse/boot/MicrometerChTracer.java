package io.blinkhouse.boot;

import io.blinkhouse.core.observability.ChTracer;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;

/**
 * Micrometer Tracing-backed implementation of {@link ChTracer}.
 *
 * <p>Each ClickHouse operation opens a child span tagged with:
 * <ul>
 *   <li>{@code db.system} = {@code clickhouse}</li>
 *   <li>{@code db.statement} = the parameterised SQL (values never attached — NFR-6)</li>
 *   <li>{@code ch.query_id} = the correlatable query ID for {@code system.query_log} joins</li>
 * </ul>
 *
 * <p>On error, the span is tagged with {@code error=true} and the exception message
 * (never stack traces or bound parameter values).
 */
public final class MicrometerChTracer implements ChTracer {

    private final Tracer tracer;

    /**
     * Constructs a tracer backed by the given Micrometer {@link Tracer}.
     *
     * @param tracer the Micrometer tracer (from the OTel or Brave bridge)
     */
    public MicrometerChTracer(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public Object startSpan(String operationName, String parameterisedSql, String queryId) {
        Span span = tracer.nextSpan()
                .name(operationName)
                .tag("db.system", "clickhouse")
                .tag("db.statement", parameterisedSql)
                .tag("ch.query_id", queryId)
                .start();
        return tracer.withSpan(span);
    }

    @Override
    public void endSpan(Object spanHandle, Throwable error) {
        if (!(spanHandle instanceof io.micrometer.tracing.Tracer.SpanInScope)) {
            return;
        }
        io.micrometer.tracing.Tracer.SpanInScope scope =
                (io.micrometer.tracing.Tracer.SpanInScope) spanHandle;
        Span currentSpan = tracer.currentSpan();
        if (error != null && currentSpan != null) {
            currentSpan.tag("error", "true")
                       .tag("error.message", error.getMessage() != null ? error.getMessage() : error.getClass().getName());
        }
        if (currentSpan != null) {
            currentSpan.end();
        }
        scope.close();
    }
}

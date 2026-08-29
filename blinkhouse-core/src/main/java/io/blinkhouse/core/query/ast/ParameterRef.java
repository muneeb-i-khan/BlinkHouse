package io.blinkhouse.core.query.ast;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A named parameter placeholder in the query AST.
 *
 * <p>The renderer emits {@code {name:Type}} syntax which ClickHouse binds
 * server-side. <strong>User-supplied values must always go through a
 * {@code ParameterRef} — never into a {@link Literal}.</strong>
 *
 * <p>Parameters are auto-named ({@code p1}, {@code p2}, …) when created via
 * {@link #ofValue(Object)}. Use {@link #of(String, Object)} for a descriptive name.
 */
public record ParameterRef(String name, Object value) implements Expression {

    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    /**
     * Creates a named parameter bound to the given value.
     *
     * @param name  the parameter name (used in the rendered SQL placeholder)
     * @param value the value to bind; may not be {@code null} for typed binding
     * @return a new ParameterRef
     */
    public static ParameterRef of(String name, Object value) {
        return new ParameterRef(name, value);
    }

    /**
     * Creates an auto-named parameter bound to the given value.
     *
     * @param value the value to bind
     * @return a new ParameterRef with a generated name
     */
    public static ParameterRef ofValue(Object value) {
        return new ParameterRef("p" + COUNTER.incrementAndGet(), value);
    }
}

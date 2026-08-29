package io.blinkhouse.core.query;

import java.util.Collections;
import java.util.Map;

/**
 * The result of rendering a {@link io.blinkhouse.core.query.ast.SelectStatement} —
 * a parameterised SQL string paired with its named parameter bindings.
 *
 * <p>The SQL contains ClickHouse-style placeholders: {@code {name:Type}}.
 * The {@link #parameters()} map contains the values to substitute server-side.
 */
public record BoundStatement(String sql, Map<String, Object> parameters) {

    /**
     * Constructs a BoundStatement with an unmodifiable parameter map.
     *
     * @param sql        the rendered SQL with parameter placeholders
     * @param parameters the parameter name → value bindings
     */
    public BoundStatement {
        parameters = Collections.unmodifiableMap(parameters);
    }
}

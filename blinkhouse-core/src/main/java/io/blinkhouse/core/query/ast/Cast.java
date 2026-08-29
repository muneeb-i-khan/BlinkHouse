package io.blinkhouse.core.query.ast;

/**
 * A {@code CAST(expr AS TypeName)} expression.
 */
public record Cast(Expression expression, String targetType) implements Expression {

    /**
     * Constructs a cast node.
     *
     * @param expression the expression to cast
     * @param targetType the ClickHouse type name (e.g. {@code "UInt64"}, {@code "Nullable(String)"})
     */
    public Cast {
        if (targetType == null || targetType.isBlank()) {
            throw new IllegalArgumentException("Cast targetType must not be blank");
        }
    }

    /**
     * Creates a cast expression.
     *
     * @param expression the expression to cast
     * @param targetType the ClickHouse type name
     * @return a new Cast node
     */
    public static Cast of(Expression expression, String targetType) {
        return new Cast(expression, targetType);
    }
}

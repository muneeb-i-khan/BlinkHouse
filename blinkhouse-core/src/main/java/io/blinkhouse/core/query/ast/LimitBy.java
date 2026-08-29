package io.blinkhouse.core.query.ast;

import java.util.List;

/**
 * A ClickHouse-specific {@code LIMIT n BY col1, col2, …} clause.
 */
public record LimitBy(long count, List<Expression> byExpressions) {

    /**
     * Constructs a LIMIT BY clause with a defensive copy of the by-expressions.
     *
     * @param count         the row limit per group
     * @param byExpressions the grouping expressions
     */
    public LimitBy {
        if (count <= 0) {
            throw new IllegalArgumentException("LimitBy count must be positive");
        }
        if (byExpressions == null || byExpressions.isEmpty()) {
            throw new IllegalArgumentException("LimitBy requires at least one BY expression");
        }
        byExpressions = List.copyOf(byExpressions);
    }

    /**
     * Creates a LIMIT BY clause.
     *
     * @param count         the row limit per group
     * @param byExpressions the grouping expressions
     * @return a new LimitBy
     */
    public static LimitBy of(long count, Expression... byExpressions) {
        return new LimitBy(count, List.of(byExpressions));
    }
}

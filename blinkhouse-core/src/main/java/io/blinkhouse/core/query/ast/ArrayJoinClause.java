package io.blinkhouse.core.query.ast;

import java.util.List;

/**
 * An ARRAY JOIN clause — {@code [LEFT] ARRAY JOIN col1, col2, …}.
 */
public record ArrayJoinClause(List<Expression> arrays, boolean isLeft) {

    /**
     * Constructs an ARRAY JOIN clause with a defensive copy of its expressions.
     *
     * @param arrays the array expressions to expand
     * @param isLeft whether this is a LEFT ARRAY JOIN
     */
    public ArrayJoinClause {
        if (arrays == null || arrays.isEmpty()) {
            throw new IllegalArgumentException("ArrayJoinClause requires at least one array expression");
        }
        arrays = List.copyOf(arrays);
    }

    /**
     * Creates an inner ARRAY JOIN.
     *
     * @param arrays the array expressions to expand
     * @return a new ArrayJoinClause
     */
    public static ArrayJoinClause of(Expression... arrays) {
        return new ArrayJoinClause(List.of(arrays), false);
    }

    /**
     * Creates a LEFT ARRAY JOIN.
     *
     * @param arrays the array expressions to expand
     * @return a new ArrayJoinClause
     */
    public static ArrayJoinClause left(Expression... arrays) {
        return new ArrayJoinClause(List.of(arrays), true);
    }
}

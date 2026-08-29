package io.blinkhouse.core.query.ast;

import java.util.List;

/**
 * An {@code IN} or {@code NOT IN} predicate — {@code expr [NOT] IN (v1, v2, …)}.
 */
public record In(Expression expression, List<Expression> values, boolean negated) implements Predicate {

    /**
     * Constructs an IN predicate with a defensive copy of values.
     *
     * @param expression the expression to test
     * @param values     the set of candidate values
     * @param negated    whether this is a NOT IN predicate
     */
    public In {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("In predicate requires at least one value");
        }
        values = List.copyOf(values);
    }

    /**
     * Creates an IN predicate.
     *
     * @param expression the expression to test
     * @param values     the candidate values
     * @return a new In predicate
     */
    public static In of(Expression expression, List<Expression> values) {
        return new In(expression, values, false);
    }

    /**
     * Creates a NOT IN predicate.
     *
     * @param expression the expression to test
     * @param values     the candidate values
     * @return a new In predicate with negation
     */
    public static In notOf(Expression expression, List<Expression> values) {
        return new In(expression, values, true);
    }
}

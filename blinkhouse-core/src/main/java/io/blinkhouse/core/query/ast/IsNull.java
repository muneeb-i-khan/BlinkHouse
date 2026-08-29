package io.blinkhouse.core.query.ast;

/**
 * An {@code IS NULL} or {@code IS NOT NULL} predicate.
 */
public record IsNull(Expression expression, boolean negated) implements Predicate {

    /**
     * Creates an IS NULL predicate.
     *
     * @param expression the expression to test
     * @return a new IsNull predicate
     */
    public static IsNull of(Expression expression) {
        return new IsNull(expression, false);
    }

    /**
     * Creates an IS NOT NULL predicate.
     *
     * @param expression the expression to test
     * @return a new IsNull predicate with negation
     */
    public static IsNull isNotNull(Expression expression) {
        return new IsNull(expression, true);
    }
}

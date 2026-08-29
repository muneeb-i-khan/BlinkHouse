package io.blinkhouse.core.query.ast;

/**
 * A {@code BETWEEN} predicate — {@code expr BETWEEN low AND high}.
 */
public record Between(Expression expression, Expression low, Expression high) implements Predicate {

    /**
     * Creates a BETWEEN predicate.
     *
     * @param expression the expression to test
     * @param low        the lower bound
     * @param high       the upper bound
     * @return a new Between predicate
     */
    public static Between of(Expression expression, Expression low, Expression high) {
        return new Between(expression, low, high);
    }
}

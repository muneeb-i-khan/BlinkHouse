package io.blinkhouse.core.query.ast;

/**
 * A {@code LIKE} or {@code NOT LIKE} predicate.
 *
 * <p>The pattern must be a {@link ParameterRef} — never a {@link Literal} built
 * from user input (NFR-6).
 */
public record Like(Expression expression, Expression pattern, boolean negated) implements Predicate {

    /**
     * Creates a LIKE predicate.
     *
     * @param expression the expression to test
     * @param pattern    the pattern expression (use {@link ParameterRef} for user-supplied values)
     * @return a new Like predicate
     */
    public static Like of(Expression expression, Expression pattern) {
        return new Like(expression, pattern, false);
    }

    /**
     * Creates a NOT LIKE predicate.
     *
     * @param expression the expression to test
     * @param pattern    the pattern expression
     * @return a new Like predicate with negation
     */
    public static Like notOf(Expression expression, Expression pattern) {
        return new Like(expression, pattern, true);
    }
}

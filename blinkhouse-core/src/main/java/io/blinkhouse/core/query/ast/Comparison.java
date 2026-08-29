package io.blinkhouse.core.query.ast;

/**
 * A binary comparison predicate — {@code left op right}.
 *
 * <p>Supported operators: {@code =}, {@code !=}, {@code <>}, {@code <}, {@code <=},
 * {@code >}, {@code >=}.
 */
public record Comparison(Expression left, String operator, Expression right) implements Predicate {

    /**
     * Constructs a comparison predicate.
     *
     * @param left     the left-hand expression
     * @param operator the comparison operator
     * @param right    the right-hand expression
     */
    public Comparison {
        if (operator == null || operator.isBlank()) {
            throw new IllegalArgumentException("Comparison operator must not be blank");
        }
    }

    /**
     * Creates an equality comparison.
     *
     * @param left  left-hand expression
     * @param right right-hand expression
     * @return a new Comparison
     */
    public static Comparison eq(Expression left, Expression right) {
        return new Comparison(left, "=", right);
    }

    /**
     * Creates an inequality comparison.
     *
     * @param left  left-hand expression
     * @param right right-hand expression
     * @return a new Comparison
     */
    public static Comparison neq(Expression left, Expression right) {
        return new Comparison(left, "!=", right);
    }

    /**
     * Creates a less-than comparison.
     *
     * @param left  left-hand expression
     * @param right right-hand expression
     * @return a new Comparison
     */
    public static Comparison lt(Expression left, Expression right) {
        return new Comparison(left, "<", right);
    }

    /**
     * Creates a less-than-or-equal comparison.
     *
     * @param left  left-hand expression
     * @param right right-hand expression
     * @return a new Comparison
     */
    public static Comparison lte(Expression left, Expression right) {
        return new Comparison(left, "<=", right);
    }

    /**
     * Creates a greater-than comparison.
     *
     * @param left  left-hand expression
     * @param right right-hand expression
     * @return a new Comparison
     */
    public static Comparison gt(Expression left, Expression right) {
        return new Comparison(left, ">", right);
    }

    /**
     * Creates a greater-than-or-equal comparison.
     *
     * @param left  left-hand expression
     * @param right right-hand expression
     * @return a new Comparison
     */
    public static Comparison gte(Expression left, Expression right) {
        return new Comparison(left, ">=", right);
    }
}

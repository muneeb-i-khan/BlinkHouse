package io.blinkhouse.core.query.ast;

/**
 * A binary arithmetic or string operator, e.g. {@code a + b}, {@code a || b}.
 */
public record BinaryOp(Expression left, String operator, Expression right) implements Expression {

    /**
     * Constructs a binary operator node.
     *
     * @param left     left operand
     * @param operator the SQL operator string (e.g. {@code "+"}, {@code "-"}, {@code "||"})
     * @param right    right operand
     */
    public BinaryOp {
        if (operator == null || operator.isBlank()) {
            throw new IllegalArgumentException("BinaryOp operator must not be blank");
        }
    }

    /**
     * Creates an addition expression.
     *
     * @param left  left operand
     * @param right right operand
     * @return a new BinaryOp
     */
    public static BinaryOp add(Expression left, Expression right) {
        return new BinaryOp(left, "+", right);
    }

    /**
     * Creates a subtraction expression.
     *
     * @param left  left operand
     * @param right right operand
     * @return a new BinaryOp
     */
    public static BinaryOp subtract(Expression left, Expression right) {
        return new BinaryOp(left, "-", right);
    }

    /**
     * Creates a multiplication expression.
     *
     * @param left  left operand
     * @param right right operand
     * @return a new BinaryOp
     */
    public static BinaryOp multiply(Expression left, Expression right) {
        return new BinaryOp(left, "*", right);
    }

    /**
     * Creates a division expression.
     *
     * @param left  left operand
     * @param right right operand
     * @return a new BinaryOp
     */
    public static BinaryOp divide(Expression left, Expression right) {
        return new BinaryOp(left, "/", right);
    }
}

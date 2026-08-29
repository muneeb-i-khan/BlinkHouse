package io.blinkhouse.core.query.ast;

/**
 * A unary prefix operator, e.g. {@code -x} or {@code NOT x}.
 */
public record UnaryOp(String operator, Expression operand) implements Expression {

    /**
     * Constructs a unary operator node.
     *
     * @param operator the prefix operator string
     * @param operand  the operand expression
     */
    public UnaryOp {
        if (operator == null || operator.isBlank()) {
            throw new IllegalArgumentException("UnaryOp operator must not be blank");
        }
    }

    /**
     * Creates a negation expression.
     *
     * @param operand the expression to negate
     * @return a new UnaryOp
     */
    public static UnaryOp negate(Expression operand) {
        return new UnaryOp("-", operand);
    }
}

package io.blinkhouse.core.query.ast;

/**
 * A negation of a predicate — {@code NOT predicate}.
 */
public record Not(Predicate operand) implements Predicate {

    /**
     * Creates a NOT predicate.
     *
     * @param operand the predicate to negate
     * @return a new Not predicate
     */
    public static Not of(Predicate operand) {
        return new Not(operand);
    }
}

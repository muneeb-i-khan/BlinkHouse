package io.blinkhouse.core.query.ast;

import java.util.List;

/**
 * A disjunction of two or more predicates — {@code p1 OR p2 OR …}.
 */
public record Or(List<Predicate> operands) implements Predicate {

    /**
     * Constructs an OR predicate with a defensive copy of its operands.
     *
     * @param operands the predicates to disjoin (at least two)
     */
    public Or {
        if (operands == null || operands.size() < 2) {
            throw new IllegalArgumentException("Or requires at least two operands");
        }
        operands = List.copyOf(operands);
    }

    /**
     * Creates an OR predicate.
     *
     * @param operands the predicates to disjoin
     * @return a new Or predicate
     */
    public static Or of(Predicate... operands) {
        return new Or(List.of(operands));
    }
}

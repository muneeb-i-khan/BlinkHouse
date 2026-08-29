package io.blinkhouse.core.query.ast;

import java.util.List;

/**
 * A conjunction of two or more predicates — {@code p1 AND p2 AND …}.
 */
public record And(List<Predicate> operands) implements Predicate {

    /**
     * Constructs an AND predicate with a defensive copy of its operands.
     *
     * @param operands the predicates to conjoin (at least two)
     */
    public And {
        if (operands == null || operands.size() < 2) {
            throw new IllegalArgumentException("And requires at least two operands");
        }
        operands = List.copyOf(operands);
    }

    /**
     * Creates an AND predicate.
     *
     * @param operands the predicates to conjoin
     * @return a new And predicate
     */
    public static And of(Predicate... operands) {
        return new And(List.of(operands));
    }
}

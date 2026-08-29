package io.blinkhouse.core.query.ast;

/**
 * A boolean-valued {@link Expression} used in WHERE, PREWHERE, HAVING clauses.
 *
 * <p>All predicate implementations are records, making them value-transparent
 * and safe to inspect in exhaustive pattern-match switches.
 */
public sealed interface Predicate extends Expression
        permits And, Or, Not, Comparison, Between, In, IsNull, Like {
}

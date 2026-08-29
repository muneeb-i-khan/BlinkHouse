package io.blinkhouse.core.query.ast;

import java.util.List;

/**
 * A CASE WHEN … THEN … ELSE … END expression.
 */
public record CaseExpression(
        List<WhenClause> whenClauses,
        Expression elseExpr) implements Expression {

    /**
     * A single WHEN … THEN … branch.
     */
    public record WhenClause(Expression condition, Expression result) {
    }

    /**
     * Constructs the case expression with a defensive copy of the when-clauses.
     *
     * @param whenClauses one or more WHEN/THEN branches
     * @param elseExpr    the ELSE expression (may be {@code null})
     */
    public CaseExpression {
        if (whenClauses == null || whenClauses.isEmpty()) {
            throw new IllegalArgumentException("CaseExpression must have at least one WHEN clause");
        }
        whenClauses = List.copyOf(whenClauses);
    }
}

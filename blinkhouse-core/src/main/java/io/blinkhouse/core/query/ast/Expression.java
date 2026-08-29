package io.blinkhouse.core.query.ast;

/**
 * Root of the query expression hierarchy.
 *
 * <p>Every node in a ClickHouse query AST — column references, literals,
 * function calls, operators, predicates, subqueries — is an {@code Expression}.
 *
 * <p>The sealed hierarchy enables exhaustive pattern-matching in the
 * {@link io.blinkhouse.core.query.SqlRenderer renderer} and prevents
 * user code from extending the AST with unsafe nodes.
 *
 * <p><strong>Security invariant (NFR-6):</strong> there is no
 * {@code LiteralFromUserInput} subtype. All user-supplied values must be
 * wrapped in a {@link ParameterRef}; the renderer emits only named
 * {@code {name:Type}} placeholders that are bound server-side.
 */
public sealed interface Expression
        permits ColumnRef, Literal, ParameterRef, FunctionCall,
                BinaryOp, UnaryOp, CaseExpression, Cast, Aliased,
                Predicate, RawFragment {
}

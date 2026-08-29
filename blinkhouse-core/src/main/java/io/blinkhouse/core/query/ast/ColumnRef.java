package io.blinkhouse.core.query.ast;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Reference to a named column in the query, optionally qualified with a table alias.
 *
 * <p>The column name is validated against the safe-identifier pattern
 * {@code ^[A-Za-z_][0-9A-Za-z_]*$} and backtick-quoted by the renderer.
 * Dotted paths (e.g. {@code t.col}) are represented as
 * {@code tableAlias="t", name="col"}.
 */
public record ColumnRef(String tableAlias, String name) implements Expression {

    private static final Pattern SAFE_IDENT = Pattern.compile("^[A-Za-z_][0-9A-Za-z_]*$");

    /**
     * Constructs a column reference, validating both components.
     *
     * @param tableAlias optional table alias prefix (may be {@code null} or empty)
     * @param name       the column name — must match {@code ^[A-Za-z_][0-9A-Za-z_]*$}
     */
    public ColumnRef {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("ColumnRef name must not be blank");
        }
        if (!SAFE_IDENT.matcher(name).matches()) {
            throw new IllegalArgumentException(
                "ColumnRef name '" + name + "' contains unsafe characters. "
                + "Only [A-Za-z_][0-9A-Za-z_]* is allowed.");
        }
        if (tableAlias != null && !tableAlias.isEmpty()
                && !SAFE_IDENT.matcher(tableAlias).matches()) {
            throw new IllegalArgumentException(
                "ColumnRef tableAlias '" + tableAlias + "' contains unsafe characters.");
        }
    }

    /**
     * Creates an unqualified column reference.
     *
     * @param name the column name
     * @return a new ColumnRef
     */
    public static ColumnRef of(String name) {
        return new ColumnRef(null, name);
    }

    /**
     * Creates a table-qualified column reference.
     *
     * @param tableAlias the table alias
     * @param name       the column name
     * @return a new ColumnRef
     */
    public static ColumnRef of(String tableAlias, String name) {
        return new ColumnRef(tableAlias, name);
    }

    /**
     * Wraps this column reference with an alias.
     *
     * @param alias the SQL alias
     * @return an {@link Aliased} expression
     */
    public Aliased as(String alias) {
        return new Aliased(this, alias);
    }

    // ── Comparison helpers ────────────────────────────────────────────────────

    /** Wraps a value as an Expression: if already an Expression, returns it as-is. */
    private static Expression toExpr(Object value) {
        if (value instanceof Expression) {
            return (Expression) value;
        }
        return ParameterRef.ofValue(value);
    }

    /**
     * Returns a predicate asserting equality.
     *
     * @param value the right-hand value; if an {@link Expression} it is used directly,
     *              otherwise wrapped in a {@link ParameterRef}
     * @return an equality predicate
     */
    public Comparison eq(Object value) {
        return Comparison.eq(this, toExpr(value));
    }

    /**
     * Returns a predicate asserting inequality.
     *
     * @param value the right-hand value
     * @return an inequality predicate
     */
    public Comparison neq(Object value) {
        return Comparison.neq(this, toExpr(value));
    }

    /**
     * Returns a less-than predicate.
     *
     * @param value the right-hand value
     * @return a less-than predicate
     */
    public Comparison lt(Object value) {
        return Comparison.lt(this, toExpr(value));
    }

    /**
     * Returns a less-than-or-equal predicate.
     *
     * @param value the right-hand value
     * @return a less-than-or-equal predicate
     */
    public Comparison lte(Object value) {
        return Comparison.lte(this, toExpr(value));
    }

    /**
     * Returns a greater-than predicate.
     *
     * @param value the right-hand value
     * @return a greater-than predicate
     */
    public Comparison gt(Object value) {
        return Comparison.gt(this, toExpr(value));
    }

    /**
     * Returns a greater-than-or-equal predicate.
     *
     * @param value the right-hand value
     * @return a greater-than-or-equal predicate
     */
    public Comparison gte(Object value) {
        return Comparison.gte(this, toExpr(value));
    }

    /**
     * Returns a BETWEEN predicate.
     *
     * @param lo the lower bound (inclusive)
     * @param hi the upper bound (inclusive)
     * @return a between predicate
     */
    public Between between(Object lo, Object hi) {
        return Between.of(this, toExpr(lo), toExpr(hi));
    }

    /**
     * Returns an IN predicate for a list of values.
     *
     * @param values the allowed values
     * @return an in predicate
     */
    public In in(List<?> values) {
        List<Expression> exprs = values.stream()
                .map(ParameterRef::ofValue)
                .collect(Collectors.toList());
        return In.of(this, exprs);
    }

    /**
     * Returns an IN predicate for varargs values.
     *
     * @param values the allowed values
     * @return an in predicate
     */
    public In in(Object... values) {
        List<Expression> exprs = java.util.Arrays.stream(values)
                .map(ParameterRef::ofValue)
                .collect(Collectors.toList());
        return In.of(this, exprs);
    }

    /**
     * Returns a NOT IN predicate.
     *
     * @param values the disallowed values
     * @return a not-in predicate
     */
    public In notIn(List<?> values) {
        List<Expression> exprs = values.stream()
                .map(ParameterRef::ofValue)
                .collect(Collectors.toList());
        return In.notOf(this, exprs);
    }

    /**
     * Returns an IS NULL predicate.
     *
     * @return an is-null predicate
     */
    public IsNull isNull() {
        return IsNull.of(this);
    }

    /**
     * Returns an IS NOT NULL predicate.
     *
     * @return an is-not-null predicate
     */
    public IsNull isNotNull() {
        return IsNull.isNotNull(this);
    }

    /**
     * Returns a LIKE predicate.
     *
     * @param pattern the LIKE pattern; if an {@link Expression} used directly,
     *                otherwise wrapped as a {@link ParameterRef}
     * @return a like predicate
     */
    public Like like(Object pattern) {
        return Like.of(this, toExpr(pattern));
    }

    /**
     * Returns a NOT LIKE predicate.
     *
     * @param pattern the LIKE pattern; if an {@link Expression} used directly,
     *                otherwise wrapped as a {@link ParameterRef}
     * @return a not-like predicate
     */
    public Like notLike(Object pattern) {
        return Like.notOf(this, toExpr(pattern));
    }

    /**
     * Returns an ascending order specification for this column.
     *
     * @return an ascending OrderSpec
     */
    public OrderSpec asc() {
        return OrderSpec.asc(this);
    }

    /**
     * Returns a descending order specification for this column.
     *
     * @return a descending OrderSpec
     */
    public OrderSpec desc() {
        return OrderSpec.desc(this);
    }
}

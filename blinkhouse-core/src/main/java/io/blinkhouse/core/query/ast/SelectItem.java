package io.blinkhouse.core.query.ast;

/**
 * A single item in a SELECT clause — either a plain expression or an aliased one.
 *
 * <p>Use {@link Aliased} when you need {@code expr AS alias}. Wrap in a SelectItem
 * via {@link #of(Expression)}.
 */
public record SelectItem(Expression expression) {

    /**
     * Creates a select item from any expression, including aliased ones.
     *
     * @param expression the expression to select
     * @return a new SelectItem
     */
    public static SelectItem of(Expression expression) {
        return new SelectItem(expression);
    }

    /**
     * Creates a select item from a column reference.
     *
     * @param name the column name
     * @return a new SelectItem backed by a ColumnRef
     */
    public static SelectItem col(String name) {
        return new SelectItem(ColumnRef.of(name));
    }

    /**
     * Creates a wildcard SELECT * item.
     *
     * @return a SelectItem backed by a RawFragment containing {@code *}
     */
    public static SelectItem star() {
        return new SelectItem(RawFragment.of("*"));
    }
}

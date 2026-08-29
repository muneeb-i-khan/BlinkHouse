package io.blinkhouse.core.query.ast;

/**
 * An internal constant literal embedded directly in SQL.
 *
 * <p><strong>Security invariant (NFR-6):</strong> {@code Literal} values are
 * produced only from trusted internal constants (e.g. numeric constants,
 * boolean flags). There is no public factory that accepts arbitrary user input —
 * see {@link ParameterRef} for user-supplied values.
 *
 * <p>The renderer inlines the value directly without quoting for numbers and
 * booleans, and with single-quote escaping for strings (internal use only).
 */
public record Literal(Object value) implements Expression {

    /** The SQL literal {@code NULL}. */
    public static final Literal NULL = new Literal(null);

    /** The SQL literal {@code 1} (true). */
    public static final Literal TRUE = new Literal(1L);

    /** The SQL literal {@code 0} (false). */
    public static final Literal FALSE = new Literal(0L);

    /**
     * Creates a numeric literal from a long value.
     *
     * @param v the long value
     * @return a literal node
     */
    public static Literal of(long v) {
        return new Literal(v);
    }

    /**
     * Creates a numeric literal from a double value.
     *
     * @param v the double value
     * @return a literal node
     */
    public static Literal of(double v) {
        return new Literal(v);
    }

    /**
     * Returns the raw SQL representation for use by the renderer.
     *
     * @return the SQL string for this literal
     */
    public String rawSql() {
        if (value == null) {
            return "NULL";
        }
        return String.valueOf(value);
    }
}

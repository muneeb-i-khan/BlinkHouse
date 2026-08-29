package io.blinkhouse.core.query.ast;

/**
 * An ORDER BY term — {@code expr [ASC|DESC] [NULLS FIRST|LAST]}.
 */
public record OrderSpec(Expression expression, Direction direction, NullsOrder nullsOrder) {

    /** Sort direction. */
    public enum Direction {
        /** Ascending order. */
        ASC,
        /** Descending order. */
        DESC
    }

    /** Null ordering within the sort. */
    public enum NullsOrder {
        /** Nulls appear first. */
        FIRST,
        /** Nulls appear last. */
        LAST
    }

    /**
     * Creates an ascending order specification.
     *
     * @param expression the expression to sort by
     * @return a new OrderSpec
     */
    public static OrderSpec asc(Expression expression) {
        return new OrderSpec(expression, Direction.ASC, null);
    }

    /**
     * Creates a descending order specification.
     *
     * @param expression the expression to sort by
     * @return a new OrderSpec
     */
    public static OrderSpec desc(Expression expression) {
        return new OrderSpec(expression, Direction.DESC, null);
    }

    /**
     * Returns a copy of this OrderSpec with NULLS FIRST applied.
     *
     * @return a new OrderSpec with nullsOrder set to FIRST
     */
    public OrderSpec nullsFirst() {
        return new OrderSpec(expression, direction, NullsOrder.FIRST);
    }

    /**
     * Returns a copy of this OrderSpec with NULLS LAST applied.
     *
     * @return a new OrderSpec with nullsOrder set to LAST
     */
    public OrderSpec nullsLast() {
        return new OrderSpec(expression, direction, NullsOrder.LAST);
    }
}

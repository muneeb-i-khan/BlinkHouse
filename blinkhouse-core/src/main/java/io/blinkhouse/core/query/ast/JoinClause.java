package io.blinkhouse.core.query.ast;

/**
 * A JOIN clause in a SELECT statement.
 */
public record JoinClause(JoinType joinType, TableRef table, Predicate condition) {

    /** The type of join. */
    public enum JoinType {
        /** INNER JOIN. */
        INNER,
        /** LEFT OUTER JOIN. */
        LEFT,
        /** RIGHT OUTER JOIN. */
        RIGHT,
        /** FULL OUTER JOIN. */
        FULL,
        /** CROSS JOIN. */
        CROSS,
        /** LEFT SEMI JOIN (ClickHouse-specific). */
        LEFT_SEMI,
        /** RIGHT SEMI JOIN (ClickHouse-specific). */
        RIGHT_SEMI,
        /** LEFT ANTI JOIN (ClickHouse-specific). */
        LEFT_ANTI,
        /** RIGHT ANTI JOIN (ClickHouse-specific). */
        RIGHT_ANTI,
        /** LEFT ANY JOIN (ClickHouse-specific). */
        LEFT_ANY,
        /** RIGHT ANY JOIN (ClickHouse-specific). */
        RIGHT_ANY
    }

    /**
     * Creates an INNER JOIN.
     *
     * @param table     the table to join
     * @param condition the join predicate
     * @return a new JoinClause
     */
    public static JoinClause inner(TableRef table, Predicate condition) {
        return new JoinClause(JoinType.INNER, table, condition);
    }

    /**
     * Creates a LEFT JOIN.
     *
     * @param table     the table to join
     * @param condition the join predicate
     * @return a new JoinClause
     */
    public static JoinClause left(TableRef table, Predicate condition) {
        return new JoinClause(JoinType.LEFT, table, condition);
    }
}

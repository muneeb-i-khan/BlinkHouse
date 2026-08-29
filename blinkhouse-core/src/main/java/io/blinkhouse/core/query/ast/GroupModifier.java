package io.blinkhouse.core.query.ast;

/**
 * GROUP BY modifier for ClickHouse's cube/rollup/totals extensions.
 */
public enum GroupModifier {
    /** Appends {@code WITH TOTALS} — adds a grand-total row. */
    WITH_TOTALS,
    /** Appends {@code WITH ROLLUP} — generates sub-total rows. */
    WITH_ROLLUP,
    /** Appends {@code WITH CUBE} — generates all sub-total combinations. */
    WITH_CUBE
}

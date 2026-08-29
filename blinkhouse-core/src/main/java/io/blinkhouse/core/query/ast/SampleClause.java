package io.blinkhouse.core.query.ast;

/**
 * A ClickHouse {@code SAMPLE factor} clause for approximate queries.
 *
 * <p>The sample factor is a fraction between 0 and 1 (e.g. {@code 0.1} for 10%).
 */
public record SampleClause(double factor) {

    /**
     * Constructs a SAMPLE clause, validating the factor.
     *
     * @param factor the sample fraction (must be in the range (0, 1])
     */
    public SampleClause {
        if (factor <= 0.0 || factor > 1.0) {
            throw new IllegalArgumentException("SampleClause factor must be in (0, 1]: " + factor);
        }
    }

    /**
     * Creates a SAMPLE clause.
     *
     * @param factor the sample fraction
     * @return a new SampleClause
     */
    public static SampleClause of(double factor) {
        return new SampleClause(factor);
    }
}

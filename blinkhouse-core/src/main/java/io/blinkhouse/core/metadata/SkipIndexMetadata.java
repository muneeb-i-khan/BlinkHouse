package io.blinkhouse.core.metadata;

import io.blinkhouse.core.annotation.IndexType;
import java.util.List;

/**
 * Resolved metadata for a single ClickHouse data-skipping index, derived from a
 * {@link io.blinkhouse.core.annotation.ChSkipIndex} annotation.
 */
public final class SkipIndexMetadata {

    private final String name;
    private final String expression;
    private final IndexType type;
    private final int granularity;
    private final List<String> params;

    /** Constructs fully-resolved skip-index metadata. */
    public SkipIndexMetadata(
            String name,
            String expression,
            IndexType type,
            int granularity,
            List<String> params) {
        this.name = name;
        this.expression = expression;
        this.type = type;
        this.granularity = granularity;
        this.params = List.copyOf(params);
    }

    /** Index name as it appears in the DDL. */
    public String getName() {
        return name;
    }

    /** Column or expression being indexed. */
    public String getExpression() {
        return expression;
    }

    /** Index algorithm. */
    public IndexType getType() {
        return type;
    }

    /** Granule count per index block. */
    public int getGranularity() {
        return granularity;
    }

    /** Additional type-specific constructor parameters. */
    public List<String> getParams() {
        return params;
    }
}

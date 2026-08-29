package io.blinkhouse.core.schema;

/**
 * Live state of a data-skipping index as read from {@code system.data_skipping_indices}.
 */
public final class LiveIndex {

    private final String name;
    private final String expression;
    private final String type;
    private final int granularity;

    /** Constructs a live index descriptor. */
    public LiveIndex(String name, String expression, String type, int granularity) {
        this.name = name;
        this.expression = expression;
        this.type = type;
        this.granularity = granularity;
    }

    /** Index name. */
    public String getName() {
        return name;
    }

    /** Indexed expression or column. */
    public String getExpression() {
        return expression;
    }

    /** Index type string as returned by the server, e.g. {@code "minmax"}, {@code "tokenbf_v1"}. */
    public String getType() {
        return type;
    }

    /** Granularity of the index. */
    public int getGranularity() {
        return granularity;
    }
}

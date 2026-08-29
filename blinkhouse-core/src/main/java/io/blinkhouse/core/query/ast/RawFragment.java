package io.blinkhouse.core.query.ast;

/**
 * An escape hatch for raw SQL fragments.
 *
 * <p><strong>Caution:</strong> the fragment is emitted verbatim with no escaping.
 * Use only for ClickHouse-specific syntax that the AST cannot express otherwise.
 * Never construct a fragment from user input (NFR-6).
 */
public record RawFragment(String sql) implements Expression {

    /**
     * Constructs a raw fragment.
     *
     * @param sql the literal SQL text to emit (must not be blank)
     */
    public RawFragment {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("RawFragment sql must not be blank");
        }
    }

    /**
     * Creates a raw SQL fragment.
     *
     * @param sql the literal SQL text
     * @return a new RawFragment
     */
    public static RawFragment of(String sql) {
        return new RawFragment(sql);
    }
}

package io.blinkhouse.core.query.ast;

/**
 * An aliased expression — {@code expr AS alias}.
 */
public record Aliased(Expression expression, String alias) implements Expression {

    /**
     * Constructs an aliased expression.
     *
     * @param expression the underlying expression
     * @param alias      the alias name (must not be blank)
     */
    public Aliased {
        if (alias == null || alias.isBlank()) {
            throw new IllegalArgumentException("Aliased alias must not be blank");
        }
    }
}

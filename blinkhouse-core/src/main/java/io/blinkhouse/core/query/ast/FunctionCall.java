package io.blinkhouse.core.query.ast;

import java.util.List;

/**
 * A ClickHouse function invocation, e.g. {@code toStartOfHour(ts)}, {@code uniq(user_id)}.
 *
 * <p>Function names are validated to contain only safe characters.
 * Arguments are {@link Expression} nodes — never raw strings.
 */
public record FunctionCall(String name, List<Expression> args) implements Expression {

    /**
     * Constructs a function call, copying the argument list defensively.
     *
     * @param name the function name (must not be blank)
     * @param args the argument expressions
     */
    public FunctionCall {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("FunctionCall name must not be blank");
        }
        args = List.copyOf(args);
    }

    /**
     * Creates a function call with the given name and arguments.
     *
     * @param name the function name
     * @param args the argument expressions
     * @return a new FunctionCall
     */
    public static FunctionCall of(String name, Expression... args) {
        return new FunctionCall(name, List.of(args));
    }

    /**
     * Creates a function call with a list of arguments.
     *
     * @param name the function name
     * @param args the argument expressions
     * @return a new FunctionCall
     */
    public static FunctionCall of(String name, List<Expression> args) {
        return new FunctionCall(name, args);
    }

    /**
     * Wraps this function call with an alias.
     *
     * @param alias the SQL alias
     * @return an {@link Aliased} expression
     */
    public Aliased as(String alias) {
        return new Aliased(this, alias);
    }

    /**
     * Returns an ascending order specification for this expression.
     *
     * @return an ascending OrderSpec
     */
    public OrderSpec asc() {
        return new OrderSpec(this, OrderSpec.Direction.ASC, null);
    }

    /**
     * Returns a descending order specification for this expression.
     *
     * @return a descending OrderSpec
     */
    public OrderSpec desc() {
        return new OrderSpec(this, OrderSpec.Direction.DESC, null);
    }
}

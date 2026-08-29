package io.blinkhouse.core.query.ast;

import java.util.List;

/**
 * A complete SELECT statement in the BlinkHouse AST.
 *
 * <p>Supports ClickHouse-specific clauses: {@code PREWHERE}, {@code FINAL},
 * {@code SAMPLE}, {@code LIMIT n BY}, {@code WITH TOTALS/ROLLUP/CUBE}.
 *
 * <p>Build via {@link io.blinkhouse.core.query.ChQuery}.
 */
public record SelectStatement(
        List<SelectItem> select,
        TableRef from,
        boolean isFinal,
        SampleClause sample,
        List<JoinClause> joins,
        List<ArrayJoinClause> arrayJoins,
        Predicate prewhere,
        Predicate where,
        List<Expression> groupBy,
        GroupModifier groupModifier,
        Predicate having,
        List<OrderSpec> orderBy,
        LimitBy limitBy,
        Long limit,
        Long offset) {

    /**
     * Constructs a SelectStatement, applying defensive copies on all lists.
     */
    public SelectStatement {
        if (select == null || select.isEmpty()) {
            throw new IllegalArgumentException("SelectStatement must have at least one SELECT item");
        }
        select = List.copyOf(select);
        joins = joins != null ? List.copyOf(joins) : List.of();
        arrayJoins = arrayJoins != null ? List.copyOf(arrayJoins) : List.of();
        groupBy = groupBy != null ? List.copyOf(groupBy) : List.of();
        orderBy = orderBy != null ? List.copyOf(orderBy) : List.of();
    }
}

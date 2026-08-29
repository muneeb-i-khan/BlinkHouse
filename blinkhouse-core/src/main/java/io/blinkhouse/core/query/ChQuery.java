package io.blinkhouse.core.query;

import io.blinkhouse.core.query.ast.ArrayJoinClause;
import io.blinkhouse.core.query.ast.Expression;
import io.blinkhouse.core.query.ast.GroupModifier;
import io.blinkhouse.core.query.ast.JoinClause;
import io.blinkhouse.core.query.ast.LimitBy;
import io.blinkhouse.core.query.ast.OrderSpec;
import io.blinkhouse.core.query.ast.Predicate;
import io.blinkhouse.core.query.ast.SampleClause;
import io.blinkhouse.core.query.ast.SelectItem;
import io.blinkhouse.core.query.ast.SelectStatement;
import io.blinkhouse.core.query.ast.TableRef;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for {@link SelectStatement}.
 *
 * <pre>{@code
 * SelectStatement q = ChQuery.select(col("event"), Functions.count())
 *         .from(TableRef.of("page_views"))
 *         .where(col("ts").gte(ParameterRef.of("start", startTs)))
 *         .groupBy(col("event"))
 *         .orderBy(OrderSpec.desc(Functions.count()))
 *         .limit(100)
 *         .build();
 * }</pre>
 */
public final class ChQuery {

    private final List<SelectItem> select;
    private TableRef from;
    private boolean isFinal;
    private SampleClause sample;
    private final List<JoinClause> joins = new ArrayList<>();
    private final List<ArrayJoinClause> arrayJoins = new ArrayList<>();
    private Predicate prewhere;
    private Predicate where;
    private final List<Expression> groupBy = new ArrayList<>();
    private GroupModifier groupModifier;
    private Predicate having;
    private final List<OrderSpec> orderBy = new ArrayList<>();
    private LimitBy limitBy;
    private Long limit;
    private Long offset;

    private ChQuery(List<SelectItem> select) {
        this.select = new ArrayList<>(select);
    }

    /**
     * Starts a SELECT with the given items.
     *
     * @param items the SELECT expressions
     * @return a new ChQuery builder
     */
    public static ChQuery select(SelectItem... items) {
        return new ChQuery(List.of(items));
    }

    /**
     * Starts a SELECT with a list of items.
     *
     * @param items the SELECT expressions
     * @return a new ChQuery builder
     */
    public static ChQuery select(List<SelectItem> items) {
        return new ChQuery(items);
    }

    /**
     * Sets the FROM table.
     *
     * @param tableRef the source table
     * @return this builder
     */
    public ChQuery from(TableRef tableRef) {
        this.from = tableRef;
        return this;
    }

    /**
     * Adds FINAL to the query (for ReplacingMergeTree deduplication).
     *
     * @return this builder
     */
    public ChQuery finalModifier() {
        this.isFinal = true;
        return this;
    }

    /**
     * Adds a SAMPLE clause.
     *
     * @param factor the sample fraction (0 &lt; factor ≤ 1)
     * @return this builder
     */
    public ChQuery sample(double factor) {
        this.sample = SampleClause.of(factor);
        return this;
    }

    /**
     * Adds a JOIN clause.
     *
     * @param join the join clause
     * @return this builder
     */
    public ChQuery join(JoinClause join) {
        this.joins.add(join);
        return this;
    }

    /**
     * Adds an ARRAY JOIN clause.
     *
     * @param arrayJoin the array join clause
     * @return this builder
     */
    public ChQuery arrayJoin(ArrayJoinClause arrayJoin) {
        this.arrayJoins.add(arrayJoin);
        return this;
    }

    /**
     * Sets the PREWHERE predicate (ClickHouse primary-key pre-filter).
     *
     * @param predicate the PREWHERE predicate
     * @return this builder
     */
    public ChQuery prewhere(Predicate predicate) {
        this.prewhere = predicate;
        return this;
    }

    /**
     * Sets the WHERE predicate.
     *
     * @param predicate the WHERE predicate
     * @return this builder
     */
    public ChQuery where(Predicate predicate) {
        this.where = predicate;
        return this;
    }

    /**
     * Sets the GROUP BY expressions.
     *
     * @param expressions the grouping expressions
     * @return this builder
     */
    public ChQuery groupBy(Expression... expressions) {
        this.groupBy.addAll(List.of(expressions));
        return this;
    }

    /**
     * Appends a GROUP BY modifier (WITH TOTALS / ROLLUP / CUBE).
     *
     * @param modifier the group modifier
     * @return this builder
     */
    public ChQuery groupModifier(GroupModifier modifier) {
        this.groupModifier = modifier;
        return this;
    }

    /**
     * Sets the HAVING predicate.
     *
     * @param predicate the HAVING predicate
     * @return this builder
     */
    public ChQuery having(Predicate predicate) {
        this.having = predicate;
        return this;
    }

    /**
     * Appends ORDER BY terms.
     *
     * @param specs the order specifications
     * @return this builder
     */
    public ChQuery orderBy(OrderSpec... specs) {
        this.orderBy.addAll(List.of(specs));
        return this;
    }

    /**
     * Sets the LIMIT n BY clause.
     *
     * @param limitBy the limit-by clause
     * @return this builder
     */
    public ChQuery limitBy(LimitBy limitBy) {
        this.limitBy = limitBy;
        return this;
    }

    /**
     * Sets the LIMIT.
     *
     * @param count the maximum number of rows to return
     * @return this builder
     */
    public ChQuery limit(long count) {
        this.limit = count;
        return this;
    }

    /**
     * Sets the OFFSET.
     *
     * @param count the number of rows to skip
     * @return this builder
     */
    public ChQuery offset(long count) {
        this.offset = count;
        return this;
    }

    /**
     * Builds the immutable {@link SelectStatement}.
     *
     * @return the constructed statement
     * @throws IllegalStateException if no FROM clause was set
     */
    public SelectStatement build() {
        if (from == null) {
            throw new IllegalStateException("ChQuery.from() must be set before build()");
        }
        return new SelectStatement(
                select,
                from,
                isFinal,
                sample,
                joins,
                arrayJoins,
                prewhere,
                where,
                groupBy,
                groupModifier,
                having,
                orderBy,
                limitBy,
                limit,
                offset);
    }

    /**
     * Convenience: builds and immediately renders to a {@link BoundStatement}.
     *
     * @return the bound SQL statement
     */
    public BoundStatement render() {
        return SqlRenderer.render(build());
    }
}

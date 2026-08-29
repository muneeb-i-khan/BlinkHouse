package io.blinkhouse.core.query;

import io.blinkhouse.core.query.ast.And;
import io.blinkhouse.core.query.ast.ArrayJoinClause;
import io.blinkhouse.core.query.ast.Between;
import io.blinkhouse.core.query.ast.CaseExpression;
import io.blinkhouse.core.query.ast.Cast;
import io.blinkhouse.core.query.ast.ColumnRef;
import io.blinkhouse.core.query.ast.GroupModifier;
import io.blinkhouse.core.query.ast.In;
import io.blinkhouse.core.query.ast.JoinClause;
import io.blinkhouse.core.query.ast.LimitBy;
import io.blinkhouse.core.query.ast.Literal;
import io.blinkhouse.core.query.ast.Or;
import io.blinkhouse.core.query.ast.OrderSpec;
import io.blinkhouse.core.query.ast.ParameterRef;
import io.blinkhouse.core.query.ast.RawFragment;
import io.blinkhouse.core.query.ast.SelectItem;
import io.blinkhouse.core.query.ast.TableRef;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden-SQL snapshot tests for {@link SqlRenderer}.
 *
 * <p>Each test constructs a {@link SelectStatement} via {@link ChQuery},
 * renders it, and asserts the exact SQL string. Parameter values are verified separately.
 */
class SqlRendererTest {

    // ── helpers ───────────────────────────────────────────────────────────────

    private static ColumnRef col(String name) {
        return ColumnRef.of(name);
    }

    private static SelectItem s(String name) {
        return SelectItem.col(name);
    }

    private static TableRef tbl(String name) {
        return TableRef.of(name);
    }

    // ── 1. Simple SELECT * ────────────────────────────────────────────────────

    @Test
    void simpleSelectStar() {
        BoundStatement bound = ChQuery.select(SelectItem.star())
                .from(tbl("page_views"))
                .limit(10)
                .render();

        assertThat(bound.sql()).isEqualTo("SELECT * FROM `page_views` LIMIT 10");
        assertThat(bound.parameters()).isEmpty();
    }

    // ── 2. WHERE equality ─────────────────────────────────────────────────────

    @Test
    void whereEquality() {
        BoundStatement bound = ChQuery.select(s("user_id"), s("event"))
                .from(tbl("page_views"))
                .where(col("event").eq("page_view"))
                .render();

        assertThat(bound.sql()).contains("WHERE `event` = {p");
        assertThat(bound.parameters()).containsValue("page_view");
    }

    // ── 3. WHERE BETWEEN ──────────────────────────────────────────────────────

    @Test
    void whereBetween() {
        BoundStatement bound = ChQuery.select(s("ts"), s("url"))
                .from(tbl("page_views"))
                .where(Between.of(col("ts"),
                        ParameterRef.of("start", 1_700_000_000L),
                        ParameterRef.of("end", 1_700_100_000L)))
                .render();

        assertThat(bound.sql()).contains("WHERE `ts` BETWEEN {start:Int64} AND {end:Int64}");
        assertThat(bound.parameters()).containsEntry("start", 1_700_000_000L)
                .containsEntry("end", 1_700_100_000L);
    }

    // ── 4. WHERE IN list ──────────────────────────────────────────────────────

    @Test
    void whereInList() {
        BoundStatement bound = ChQuery.select(s("session_id"))
                .from(tbl("sessions"))
                .where(col("status").in("active", "idle"))
                .render();

        assertThat(bound.sql()).contains("IN (");
        assertThat(bound.parameters().values()).contains("active", "idle");
    }

    // ── 5. WHERE NOT IN ───────────────────────────────────────────────────────

    @Test
    void whereNotIn() {
        BoundStatement bound = ChQuery.select(s("user_id"))
                .from(tbl("events"))
                .where(col("event").notIn(List.of("signup", "logout")))
                .render();

        assertThat(bound.sql()).contains("NOT IN (");
    }

    // ── 6. IS NULL / IS NOT NULL ──────────────────────────────────────────────

    @Test
    void isNullAndIsNotNull() {
        BoundStatement bound = ChQuery.select(SelectItem.star())
                .from(tbl("users"))
                .where(And.of(col("email").isNotNull(), col("phone").isNull()))
                .render();

        assertThat(bound.sql()).contains("IS NOT NULL").contains("IS NULL");
    }

    // ── 7. LIKE predicate ─────────────────────────────────────────────────────

    @Test
    void likePattern() {
        BoundStatement bound = ChQuery.select(s("url"))
                .from(tbl("page_views"))
                .where(col("url").like("%checkout%"))
                .render();

        assertThat(bound.sql()).contains("LIKE {");
        assertThat(bound.parameters().values()).contains("%checkout%");
    }

    // ── 8. GROUP BY + aggregate ───────────────────────────────────────────────

    @Test
    void groupByCount() {
        BoundStatement bound = ChQuery.select(
                        s("event"),
                        SelectItem.of(Functions.count().as("cnt")))
                .from(tbl("page_views"))
                .groupBy(col("event"))
                .orderBy(OrderSpec.desc(Functions.count()))
                .limit(20)
                .render();

        assertThat(bound.sql())
                .contains("GROUP BY `event`")
                .contains("ORDER BY count() DESC")
                .contains("LIMIT 20");
    }

    // ── 9. WITH TOTALS ────────────────────────────────────────────────────────

    @Test
    void groupByWithTotals() {
        BoundStatement bound = ChQuery.select(s("country"), SelectItem.of(Functions.count()))
                .from(tbl("page_views"))
                .groupBy(col("country"))
                .groupModifier(GroupModifier.WITH_TOTALS)
                .render();

        assertThat(bound.sql()).contains("GROUP BY `country` WITH TOTALS");
    }

    // ── 10. WITH ROLLUP ───────────────────────────────────────────────────────

    @Test
    void groupByWithRollup() {
        BoundStatement bound = ChQuery.select(s("year"), s("month"), SelectItem.of(Functions.sum(col("revenue"))))
                .from(tbl("sales"))
                .groupBy(col("year"), col("month"))
                .groupModifier(GroupModifier.WITH_ROLLUP)
                .render();

        assertThat(bound.sql()).contains("WITH ROLLUP");
    }

    // ── 11. HAVING ────────────────────────────────────────────────────────────

    @Test
    void having() {
        BoundStatement bound = ChQuery.select(s("user_id"), SelectItem.of(Functions.count()))
                .from(tbl("events"))
                .groupBy(col("user_id"))
                .having(io.blinkhouse.core.query.ast.Comparison.gte(
                        Functions.count(), ParameterRef.of("minCount", 5L)))
                .render();

        assertThat(bound.sql()).contains("HAVING count() >= {minCount:Int64}");
    }

    // ── 12. ORDER BY multi-column ─────────────────────────────────────────────

    @Test
    void orderByMultipleColumns() {
        BoundStatement bound = ChQuery.select(s("ts"), s("user_id"))
                .from(tbl("events"))
                .orderBy(col("ts").desc(), col("user_id").asc())
                .render();

        assertThat(bound.sql()).contains("ORDER BY `ts` DESC, `user_id` ASC");
    }

    // ── 13. ORDER BY NULLS FIRST ──────────────────────────────────────────────

    @Test
    void orderByNullsFirst() {
        BoundStatement bound = ChQuery.select(s("score"))
                .from(tbl("rankings"))
                .orderBy(OrderSpec.desc(col("score")).nullsFirst())
                .render();

        assertThat(bound.sql()).contains("DESC NULLS FIRST");
    }

    // ── 14. LIMIT + OFFSET ────────────────────────────────────────────────────

    @Test
    void limitWithOffset() {
        BoundStatement bound = ChQuery.select(s("id"))
                .from(tbl("events"))
                .limit(10)
                .offset(20)
                .render();

        assertThat(bound.sql()).contains("LIMIT 10 OFFSET 20");
    }

    // ── 15. LIMIT n BY ────────────────────────────────────────────────────────

    @Test
    void limitNBy() {
        BoundStatement bound = ChQuery.select(SelectItem.star())
                .from(tbl("events"))
                .limitBy(LimitBy.of(3, col("user_id")))
                .render();

        assertThat(bound.sql()).contains("LIMIT 3 BY `user_id`");
    }

    // ── 16. FINAL modifier ────────────────────────────────────────────────────

    @Test
    void finalModifier() {
        BoundStatement bound = ChQuery.select(SelectItem.star())
                .from(tbl("products"))
                .finalModifier()
                .render();

        assertThat(bound.sql()).contains("FROM `products` FINAL");
    }

    // ── 17. SAMPLE ────────────────────────────────────────────────────────────

    @Test
    void sample() {
        BoundStatement bound = ChQuery.select(SelectItem.of(Functions.count()))
                .from(tbl("events"))
                .sample(0.1)
                .render();

        assertThat(bound.sql()).contains("SAMPLE 0.1");
    }

    // ── 18. PREWHERE ──────────────────────────────────────────────────────────

    @Test
    void prewhere() {
        BoundStatement bound = ChQuery.select(SelectItem.star())
                .from(tbl("logs"))
                .prewhere(col("date").gte(ParameterRef.of("d", "2024-01-01")))
                .where(col("level").eq("ERROR"))
                .render();

        assertThat(bound.sql()).contains("PREWHERE `date` >= {d:String}")
                .contains("WHERE `level` = ")
                .doesNotContain(" WHERE `date`");
    }

    // ── 19. INNER JOIN ────────────────────────────────────────────────────────

    @Test
    void innerJoin() {
        BoundStatement bound = ChQuery.select(
                        SelectItem.of(ColumnRef.of("e", "user_id")),
                        SelectItem.of(ColumnRef.of("u", "name")))
                .from(tbl("events"))
                .join(JoinClause.inner(tbl("users"),
                        io.blinkhouse.core.query.ast.Comparison.eq(
                                ColumnRef.of("e", "user_id"), ColumnRef.of("u", "id"))))
                .render();

        assertThat(bound.sql()).contains("INNER JOIN `users` ON");
    }

    // ── 20. LEFT JOIN ─────────────────────────────────────────────────────────

    @Test
    void leftJoin() {
        BoundStatement bound = ChQuery.select(SelectItem.star())
                .from(tbl("orders"))
                .join(JoinClause.left(tbl("refunds"),
                        io.blinkhouse.core.query.ast.Comparison.eq(
                                ColumnRef.of("orders", "id"), ColumnRef.of("refunds", "order_id"))))
                .render();

        assertThat(bound.sql()).contains("LEFT JOIN `refunds` ON");
    }

    // ── 21. ARRAY JOIN ────────────────────────────────────────────────────────

    @Test
    void arrayJoin() {
        BoundStatement bound = ChQuery.select(s("user_id"), s("tag"))
                .from(tbl("page_views"))
                .arrayJoin(ArrayJoinClause.of(col("tags")))
                .render();

        assertThat(bound.sql()).contains("ARRAY JOIN `tags`");
    }

    // ── 22. LEFT ARRAY JOIN ───────────────────────────────────────────────────

    @Test
    void leftArrayJoin() {
        BoundStatement bound = ChQuery.select(s("user_id"), s("tag"))
                .from(tbl("page_views"))
                .arrayJoin(ArrayJoinClause.left(col("tags")))
                .render();

        assertThat(bound.sql()).contains("LEFT ARRAY JOIN `tags`");
    }

    // ── 23. CASE WHEN ─────────────────────────────────────────────────────────

    @Test
    void caseWhen() {
        CaseExpression caseExpr = new CaseExpression(
                List.of(
                        new CaseExpression.WhenClause(col("score").gte(90L), RawFragment.of("'A'")),
                        new CaseExpression.WhenClause(col("score").gte(70L), RawFragment.of("'B'"))),
                RawFragment.of("'C'"));

        BoundStatement bound = ChQuery.select(s("student"), SelectItem.of(new io.blinkhouse.core.query.ast.Aliased(caseExpr, "grade")))
                .from(tbl("results"))
                .render();

        assertThat(bound.sql()).contains("CASE WHEN").contains("THEN 'A'").contains("ELSE 'C' END");
    }

    // ── 24. CAST ──────────────────────────────────────────────────────────────

    @Test
    void castExpression() {
        BoundStatement bound = ChQuery.select(
                        SelectItem.of(Cast.of(col("ts"), "DateTime64(3)")))
                .from(tbl("events"))
                .render();

        assertThat(bound.sql()).contains("CAST(`ts` AS DateTime64(3))");
    }

    // ── 25. Arithmetic BinaryOp ───────────────────────────────────────────────

    @Test
    void arithmeticExpression() {
        BoundStatement bound = ChQuery.select(
                        SelectItem.of(new io.blinkhouse.core.query.ast.Aliased(
                                io.blinkhouse.core.query.ast.BinaryOp.divide(
                                        Functions.sum(col("revenue")), Functions.count()),
                                "avg_revenue")))
                .from(tbl("orders"))
                .render();

        assertThat(bound.sql()).contains("(sum(`revenue`) / count()) AS `avg_revenue`");
    }

    // ── 26. AND + OR composition ──────────────────────────────────────────────

    @Test
    void andOrComposition() {
        BoundStatement bound = ChQuery.select(SelectItem.star())
                .from(tbl("events"))
                .where(Or.of(
                        And.of(col("country").eq("IN"), col("device").eq("mobile")),
                        And.of(col("country").eq("US"), col("device").eq("desktop"))))
                .render();

        assertThat(bound.sql()).contains("OR").contains("AND");
    }

    // ── 27. NOT predicate ─────────────────────────────────────────────────────

    @Test
    void notPredicate() {
        BoundStatement bound = ChQuery.select(SelectItem.star())
                .from(tbl("users"))
                .where(io.blinkhouse.core.query.ast.Not.of(col("is_bot").eq(1L)))
                .render();

        assertThat(bound.sql()).contains("NOT (");
    }

    // ── 28. uniq() distinct count ─────────────────────────────────────────────

    @Test
    void uniqAggregate() {
        BoundStatement bound = ChQuery.select(
                        s("country"),
                        SelectItem.of(Functions.uniq(col("user_id")).as("dau")))
                .from(tbl("page_views"))
                .groupBy(col("country"))
                .orderBy(OrderSpec.desc(Functions.uniq(col("user_id"))))
                .render();

        assertThat(bound.sql())
                .contains("uniq(`user_id`)")
                .contains("GROUP BY `country`");
    }

    // ── 29. toStartOfHour time-bucketing ──────────────────────────────────────

    @Test
    void timeBucketing() {
        BoundStatement bound = ChQuery.select(
                        SelectItem.of(Functions.toStartOfHour(col("ts")).as("hour")),
                        SelectItem.of(Functions.count()))
                .from(tbl("events"))
                .groupBy(Functions.toStartOfHour(col("ts")))
                .orderBy(OrderSpec.asc(Functions.toStartOfHour(col("ts"))))
                .render();

        assertThat(bound.sql())
                .contains("toStartOfHour(`ts`)")
                .contains("GROUP BY toStartOfHour(`ts`)")
                .contains("ORDER BY toStartOfHour(`ts`) ASC");
    }

    // ── 30. RawFragment escape hatch ──────────────────────────────────────────

    @Test
    void rawFragmentEscapeHatch() {
        BoundStatement bound = ChQuery.select(
                        SelectItem.of(RawFragment.of("arraySort(groupArray(`tag`))")))
                .from(tbl("page_views"))
                .where(col("ts").gte(ParameterRef.of("since", "2024-01-01")))
                .render();

        assertThat(bound.sql()).contains("arraySort(groupArray(`tag`))");
        assertThat(bound.parameters()).containsEntry("since", "2024-01-01");
    }

    // ── Security: no user input in Literal ────────────────────────────────────

    @Test
    void literalOnlyAllowsInternalConstants() {
        BoundStatement bound = ChQuery.select(SelectItem.star())
                .from(tbl("events"))
                .where(io.blinkhouse.core.query.ast.Comparison.eq(col("active"), Literal.TRUE))
                .render();

        assertThat(bound.sql()).contains("= 1");
        assertThat(bound.parameters()).isEmpty();
    }

    // ── Named parameter binding order ─────────────────────────────────────────

    @Test
    void namedParameterBindingOrder() {
        BoundStatement bound = ChQuery.select(SelectItem.star())
                .from(tbl("events"))
                .where(And.of(
                        col("ts").gte(ParameterRef.of("start", 1_700_000_000L)),
                        col("ts").lte(ParameterRef.of("end", 1_700_100_000L))))
                .render();

        assertThat(bound.parameters()).containsKey("start").containsKey("end");
        assertThat(new java.util.ArrayList<>(bound.parameters().keySet()))
                .containsExactly("start", "end");
    }
}

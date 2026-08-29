package io.blinkhouse.core.query;

import io.blinkhouse.core.query.ast.ColumnRef;
import io.blinkhouse.core.query.ast.FunctionCall;
import io.blinkhouse.core.query.ast.RawFragment;
import io.blinkhouse.core.query.ast.SelectItem;
import io.blinkhouse.core.query.ast.SelectStatement;
import io.blinkhouse.core.query.ast.TableRef;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.blinkhouse.core.query.Functions.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for -Merge aggregate combinators and geo function helpers added in Phase 7.
 */
class FunctionsMergeAndGeoTest {

    @Test
    void uniqMergeRendersSql() {
        FunctionCall f = uniqMerge(ColumnRef.of("uv_state"));
        BoundStatement bound = SqlRenderer.renderExpression(f);
        assertThat(bound.sql()).isEqualTo("uniqMerge(`uv_state`)");
    }

    @Test
    void sumMergeRendersSql() {
        BoundStatement b = SqlRenderer.renderExpression(sumMerge(ColumnRef.of("revenue_state")));
        assertThat(b.sql()).isEqualTo("sumMerge(`revenue_state`)");
    }

    @Test
    void avgMergeRendersSql() {
        assertThat(SqlRenderer.renderExpression(avgMerge(ColumnRef.of("latency_state"))).sql())
            .isEqualTo("avgMerge(`latency_state`)");
    }

    @Test
    void minMergeRendersSql() {
        assertThat(SqlRenderer.renderExpression(minMerge(ColumnRef.of("min_state"))).sql())
            .isEqualTo("minMerge(`min_state`)");
    }

    @Test
    void maxMergeRendersSql() {
        assertThat(SqlRenderer.renderExpression(maxMerge(ColumnRef.of("max_state"))).sql())
            .isEqualTo("maxMerge(`max_state`)");
    }

    @Test
    void countMergeRendersSql() {
        assertThat(SqlRenderer.renderExpression(countMerge(ColumnRef.of("cnt_state"))).sql())
            .isEqualTo("countMerge(`cnt_state`)");
    }

    @Test
    void quantileMergeRendersSql() {
        BoundStatement b = SqlRenderer.renderExpression(quantileMerge(0.99, ColumnRef.of("q_state")));
        assertThat(b.sql()).isEqualTo("quantileMerge(0.99)(`q_state`)");
    }

    @Test
    void groupArrayMergeRendersSql() {
        assertThat(SqlRenderer.renderExpression(groupArrayMerge(ColumnRef.of("arr_state"))).sql())
            .isEqualTo("groupArrayMerge(`arr_state`)");
    }

    @Test
    void geoDistanceRendersSql() {
        BoundStatement b = SqlRenderer.renderExpression(
            geoDistance(ColumnRef.of("lon"), ColumnRef.of("lat"), ColumnRef.of("target_lon"), ColumnRef.of("target_lat")));
        assertThat(b.sql()).isEqualTo("geoDistance(`lon`, `lat`, `target_lon`, `target_lat`)");
    }

    @Test
    void pointInPolygonRendersSql() {
        BoundStatement b = SqlRenderer.renderExpression(
            pointInPolygon(ColumnRef.of("location"), ColumnRef.of("region_polygon")));
        assertThat(b.sql()).isEqualTo("pointInPolygon(`location`, `region_polygon`)");
    }

    @Test
    void geoToH3RendersSql() {
        BoundStatement b = SqlRenderer.renderExpression(
            geoToH3(ColumnRef.of("lon"), ColumnRef.of("lat"), ColumnRef.of("resolution")));
        assertThat(b.sql()).isEqualTo("geoToH3(`lon`, `lat`, `resolution`)");
    }

    @Test
    void dictGetRendersSql() {
        BoundStatement b = SqlRenderer.renderExpression(
            dictGet(
                RawFragment.of("'product_dict'"),
                RawFragment.of("'name'"),
                ColumnRef.of("product_id")));
        assertThat(b.sql()).isEqualTo("dictGet('product_dict', 'name', `product_id`)");
    }

    @Test
    void dictGetOrDefaultRendersSql() {
        BoundStatement b = SqlRenderer.renderExpression(
            dictGetOrDefault(
                RawFragment.of("'product_dict'"),
                RawFragment.of("'name'"),
                ColumnRef.of("product_id"),
                RawFragment.of("'unknown'")));
        assertThat(b.sql()).isEqualTo(
            "dictGetOrDefault('product_dict', 'name', `product_id`, 'unknown')");
    }

    @Test
    void mergeFunctionsInSelectQuery() {
        SelectStatement stmt = ChQuery.select(
                SelectItem.col("day"),
                SelectItem.of(uniqMerge(ColumnRef.of("uv_state"))),
                SelectItem.of(sumMerge(ColumnRef.of("revenue_state"))))
            .from(new TableRef("analytics", "daily_agg"))
            .build();

        BoundStatement bound = SqlRenderer.render(stmt);
        assertThat(bound.sql())
            .contains("uniqMerge(`uv_state`)")
            .contains("sumMerge(`revenue_state`)")
            .contains("FROM `")
            .contains("daily_agg`");
    }
}

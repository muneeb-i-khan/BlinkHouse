package io.blinkhouse.core.template;

import io.blinkhouse.core.query.BoundStatement;
import io.blinkhouse.core.query.SqlRenderer;
import io.blinkhouse.core.query.ast.ColumnRef;
import io.blinkhouse.core.query.ast.Comparison;
import io.blinkhouse.core.query.ast.ParameterRef;
import io.blinkhouse.core.query.ast.Predicate;
import org.junit.jupiter.api.Test;

import static io.blinkhouse.core.query.ast.ColumnRef.of;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for mutation SQL rendering (ALTER TABLE … DELETE/UPDATE).
 *
 * <p>Full end-to-end mutation tests require a live ClickHouse server and live in
 * blinkhouse-test. These tests only verify the SQL renderer's WHERE-fragment output,
 * which is what ChTemplate.delete/update delegates to.
 */
class MutationOperationsTest {

    @Test
    void deleteWhereRendersParameterisedSql() {
        Predicate where = ColumnRef.of("status").eq("inactive");
        BoundStatement bound = SqlRenderer.renderWhere(where);

        // SQL should have a placeholder, not the literal string
        assertThat(bound.sql()).contains("=");
        assertThat(bound.sql()).doesNotContain("inactive");
        // Parameter value should be bound
        assertThat(bound.parameters()).containsValue("inactive");
    }

    @Test
    void deleteWhereWithExplicitParameterRef() {
        ParameterRef param = ParameterRef.of("userId", 42L);
        Predicate where = Comparison.eq(ColumnRef.of("user_id"), param);
        BoundStatement bound = SqlRenderer.renderWhere(where);

        assertThat(bound.sql()).contains("`user_id` =");
        assertThat(bound.sql()).contains("{userId:");
        assertThat(bound.parameters()).containsEntry("userId", 42L);
    }

    @Test
    void updateSetExpressionRendersCorrectly() {
        ParameterRef newStatus = ParameterRef.of("newStatus", "active");
        BoundStatement bound = SqlRenderer.renderExpression(newStatus);

        assertThat(bound.sql()).isEqualTo("{newStatus:String}");
        assertThat(bound.parameters()).containsEntry("newStatus", "active");
    }

    @Test
    void updateSetColumnRefRendersCorrectly() {
        // Updating one column to equal another column's value
        BoundStatement bound = SqlRenderer.renderExpression(ColumnRef.of("default_region"));
        assertThat(bound.sql()).isEqualTo("`default_region`");
        assertThat(bound.parameters()).isEmpty();
    }

    @Test
    void deleteWhereWithAndPredicate() {
        Predicate where = new io.blinkhouse.core.query.ast.And(java.util.List.of(
            ColumnRef.of("status").eq("deleted"),
            ColumnRef.of("archived").eq(true)
        ));
        BoundStatement bound = SqlRenderer.renderWhere(where);

        assertThat(bound.sql()).contains("AND");
        assertThat(bound.parameters()).hasSize(2);
        assertThat(bound.parameters().values()).contains("deleted", true);
    }
}

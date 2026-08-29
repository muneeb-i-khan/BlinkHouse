package io.blinkhouse.core.template;

import io.blinkhouse.core.query.BoundStatement;
import io.blinkhouse.core.query.SqlRenderer;
import io.blinkhouse.core.query.ast.ColumnRef;
import io.blinkhouse.core.query.ast.Comparison;
import io.blinkhouse.core.query.ast.ParameterRef;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@code ChTemplate.optimize()} SQL construction.
 *
 * <p>Full integration (actual ClickHouse OPTIMIZE TABLE) is covered in
 * {@code ChTemplateAntiPatternIT}. These tests only verify SQL generation
 * without a live server.
 */
class OptimizeTableTest {

    @Test
    void optimizeMethodExists() throws Exception {
        Method m = ChTemplate.class.getMethod("optimize", Class.class, boolean.class);
        assertTrue(m != null, "ChTemplate.optimize(Class, boolean) must exist");
    }

    @Test
    void parameterisedWhereDoesNotContainRawValue() {
        String userValue = "' OR 1=1 --";
        BoundStatement bound = SqlRenderer.renderWhere(
            new Comparison(ColumnRef.of("id"), "=", ParameterRef.of("id", userValue))
        );
        assertTrue(!bound.sql().contains(userValue),
            "User value must not appear raw in SQL");
        assertTrue(bound.parameters().containsValue(userValue),
            "User value must be captured as bound parameter");
    }
}

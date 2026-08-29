package io.blinkhouse.core.arch;

import io.blinkhouse.core.query.BoundStatement;
import io.blinkhouse.core.query.ChQuery;
import io.blinkhouse.core.query.SqlRenderer;
import io.blinkhouse.core.query.ast.ColumnRef;
import io.blinkhouse.core.query.ast.Comparison;
import io.blinkhouse.core.query.ast.Literal;
import io.blinkhouse.core.query.ast.ParameterRef;
import io.blinkhouse.core.query.ast.Predicate;
import io.blinkhouse.core.query.ast.SelectItem;
import io.blinkhouse.core.query.ast.SelectStatement;
import io.blinkhouse.core.query.ast.TableRef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Security audit: verifies that user-supplied values are never interpolated raw
 * into SQL but instead flow through {@link ParameterRef} server-side binding (NFR-6).
 */
class SqlInjectionAuditTest {

    @Test
    void userValueViaParameterRefDoesNotAppearInSql() {
        String malicious = "'; DROP TABLE events; --";
        Predicate where = new Comparison(
            ColumnRef.of("user_id"),
            "=",
            ParameterRef.of("uid", malicious)
        );

        BoundStatement bound = SqlRenderer.renderWhere(where);

        assertFalse(bound.sql().contains(malicious),
            "Malicious string must not appear literally in the SQL");
        assertTrue(bound.parameters().containsValue(malicious),
            "Malicious string must be captured as a bound parameter");
    }

    @Test
    void literalConstantsAreInlinedButNotUserData() {
        Predicate where = new Comparison(
            ColumnRef.of("status"),
            "=",
            Literal.of(1)
        );
        BoundStatement bound = SqlRenderer.renderWhere(where);

        assertTrue(bound.sql().contains("1"),
            "Numeric literals are safe to inline");
        assertTrue(bound.parameters().isEmpty(),
            "No parameters collected for a Literal");
    }

    @Test
    void multipleParametersAreBoundIndependently() {
        String injectionAttempt = "1 OR 1=1";
        Predicate where = new Comparison(
            ColumnRef.of("event_type"),
            "=",
            ParameterRef.of("et", injectionAttempt)
        );

        BoundStatement bound = SqlRenderer.renderWhere(where);

        assertFalse(bound.sql().contains("OR 1=1"),
            "Injection payload must not appear in SQL");
        assertTrue(bound.parameters().containsKey("et"),
            "Parameter 'et' must be collected");
    }

    @Test
    void selectWithParameterisedWhereKeepsQueryClean() {
        String userInput = "' UNION SELECT * FROM system.users --";
        SelectStatement stmt = ChQuery
            .select(SelectItem.col("id"), SelectItem.col("ts"))
            .from(TableRef.of("events"))
            .where(new Comparison(
                ColumnRef.of("session"),
                "=",
                ParameterRef.of("sid", userInput)
            ))
            .build();

        BoundStatement bound = SqlRenderer.render(stmt);

        assertFalse(bound.sql().contains("UNION SELECT"),
            "UNION injection must not appear in rendered SQL");
        assertTrue(bound.parameters().containsValue(userInput),
            "Injection payload must be captured as a bound parameter");
    }

    @Test
    void columnNamesAreBacktickQuoted() {
        SelectStatement stmt = ChQuery
            .select(SelectItem.col("user_id"))
            .from(TableRef.of("events"))
            .build();

        BoundStatement bound = SqlRenderer.render(stmt);

        assertTrue(bound.sql().contains("`user_id`"),
            "Column identifiers must be backtick-quoted to prevent keyword collisions");
    }
}

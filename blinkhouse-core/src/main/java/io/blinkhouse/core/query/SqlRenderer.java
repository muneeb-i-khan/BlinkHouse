package io.blinkhouse.core.query;

import io.blinkhouse.core.query.ast.Aliased;
import io.blinkhouse.core.query.ast.And;
import io.blinkhouse.core.query.ast.ArrayJoinClause;
import io.blinkhouse.core.query.ast.Between;
import io.blinkhouse.core.query.ast.BinaryOp;
import io.blinkhouse.core.query.ast.Cast;
import io.blinkhouse.core.query.ast.CaseExpression;
import io.blinkhouse.core.query.ast.ColumnRef;
import io.blinkhouse.core.query.ast.Comparison;
import io.blinkhouse.core.query.ast.Expression;
import io.blinkhouse.core.query.ast.FunctionCall;
import io.blinkhouse.core.query.ast.GroupModifier;
import io.blinkhouse.core.query.ast.In;
import io.blinkhouse.core.query.ast.IsNull;
import io.blinkhouse.core.query.ast.JoinClause;
import io.blinkhouse.core.query.ast.Like;
import io.blinkhouse.core.query.ast.Literal;
import io.blinkhouse.core.query.ast.Not;
import io.blinkhouse.core.query.ast.Or;
import io.blinkhouse.core.query.ast.OrderSpec;
import io.blinkhouse.core.query.ast.ParameterRef;
import io.blinkhouse.core.query.ast.Predicate;
import io.blinkhouse.core.query.ast.RawFragment;
import io.blinkhouse.core.query.ast.SelectStatement;
import io.blinkhouse.core.query.ast.UnaryOp;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Renders a {@link SelectStatement} into a {@link BoundStatement}.
 *
 * <p>All user values are collected into named parameters; only internal
 * {@link Literal} constants and structural keywords are inlined.
 */
public final class SqlRenderer {

    private final Map<String, Object> params = new LinkedHashMap<>();

    private SqlRenderer() {
    }

    /**
     * Renders the given SELECT statement into a bound SQL string.
     *
     * @param stmt the statement to render
     * @return a BoundStatement containing SQL and collected parameters
     */
    public static BoundStatement render(SelectStatement stmt) {
        SqlRenderer r = new SqlRenderer();
        String sql = r.renderSelect(stmt);
        return new BoundStatement(sql, r.params);
    }

    // ── SELECT ─────────────────────────────────────────────────────────────────

    private String renderSelect(SelectStatement s) {
        StringBuilder sb = new StringBuilder("SELECT ");
        sb.append(s.select().stream()
                .map(i -> renderExpr(i.expression()))
                .collect(Collectors.joining(", ")));

        sb.append(" FROM `").append(s.from().qualifiedName()).append('`');

        if (s.isFinal()) {
            sb.append(" FINAL");
        }
        if (s.sample() != null) {
            sb.append(" SAMPLE ").append(s.sample().factor());
        }

        for (JoinClause j : s.joins()) {
            sb.append(' ').append(renderJoin(j));
        }
        for (ArrayJoinClause aj : s.arrayJoins()) {
            sb.append(' ').append(renderArrayJoin(aj));
        }

        if (s.prewhere() != null) {
            sb.append(" PREWHERE ").append(renderExpr(s.prewhere()));
        }
        if (s.where() != null) {
            sb.append(" WHERE ").append(renderExpr(s.where()));
        }

        if (!s.groupBy().isEmpty()) {
            sb.append(" GROUP BY ").append(s.groupBy().stream()
                    .map(this::renderExpr).collect(Collectors.joining(", ")));
            if (s.groupModifier() != null) {
                sb.append(' ').append(renderGroupModifier(s.groupModifier()));
            }
        }

        if (s.having() != null) {
            sb.append(" HAVING ").append(renderExpr(s.having()));
        }

        if (!s.orderBy().isEmpty()) {
            sb.append(" ORDER BY ").append(s.orderBy().stream()
                    .map(this::renderOrderSpec).collect(Collectors.joining(", ")));
        }

        if (s.limitBy() != null) {
            sb.append(" LIMIT ").append(s.limitBy().count()).append(" BY ")
                    .append(s.limitBy().byExpressions().stream()
                            .map(this::renderExpr).collect(Collectors.joining(", ")));
        }

        if (s.limit() != null) {
            sb.append(" LIMIT ").append(s.limit());
            if (s.offset() != null && s.offset() > 0) {
                sb.append(" OFFSET ").append(s.offset());
            }
        }

        return sb.toString();
    }

    // ── EXPRESSIONS ────────────────────────────────────────────────────────────

    private String renderExpr(Expression expr) {
        if (expr instanceof ColumnRef) {
            ColumnRef c = (ColumnRef) expr;
            if (c.tableAlias() != null && !c.tableAlias().isEmpty()) {
                return '`' + c.tableAlias() + "`.`" + c.name() + '`';
            }
            return '`' + c.name() + '`';
        }
        if (expr instanceof Literal) {
            return ((Literal) expr).rawSql();
        }
        if (expr instanceof ParameterRef) {
            ParameterRef p = (ParameterRef) expr;
            params.put(p.name(), p.value());
            return '{' + p.name() + ':' + clickHouseType(p.value()) + '}';
        }
        if (expr instanceof FunctionCall) {
            FunctionCall f = (FunctionCall) expr;
            return f.name() + '(' + f.args().stream().map(this::renderExpr)
                    .collect(Collectors.joining(", ")) + ')';
        }
        if (expr instanceof BinaryOp) {
            BinaryOp b = (BinaryOp) expr;
            return '(' + renderExpr(b.left()) + ' ' + b.operator() + ' ' + renderExpr(b.right()) + ')';
        }
        if (expr instanceof UnaryOp) {
            UnaryOp u = (UnaryOp) expr;
            return u.operator() + '(' + renderExpr(u.operand()) + ')';
        }
        if (expr instanceof Aliased) {
            Aliased a = (Aliased) expr;
            return renderExpr(a.expression()) + " AS `" + a.alias() + '`';
        }
        if (expr instanceof Cast) {
            Cast c = (Cast) expr;
            return "CAST(" + renderExpr(c.expression()) + " AS " + c.targetType() + ')';
        }
        if (expr instanceof CaseExpression) {
            return renderCase((CaseExpression) expr);
        }
        if (expr instanceof RawFragment) {
            return ((RawFragment) expr).sql();
        }
        if (expr instanceof Predicate) {
            return renderPredicate((Predicate) expr);
        }
        throw new IllegalArgumentException("Unknown expression type: " + expr.getClass().getName());
    }

    private String renderPredicate(Predicate pred) {
        if (pred instanceof Comparison) {
            Comparison c = (Comparison) pred;
            return renderExpr(c.left()) + ' ' + c.operator() + ' ' + renderExpr(c.right());
        }
        if (pred instanceof Between) {
            Between b = (Between) pred;
            return renderExpr(b.expression()) + " BETWEEN "
                    + renderExpr(b.low()) + " AND " + renderExpr(b.high());
        }
        if (pred instanceof In) {
            In i = (In) pred;
            String vals = i.values().stream().map(this::renderExpr).collect(Collectors.joining(", "));
            return renderExpr(i.expression()) + (i.negated() ? " NOT IN (" : " IN (") + vals + ')';
        }
        if (pred instanceof IsNull) {
            IsNull n = (IsNull) pred;
            return renderExpr(n.expression()) + (n.negated() ? " IS NOT NULL" : " IS NULL");
        }
        if (pred instanceof Like) {
            Like l = (Like) pred;
            return renderExpr(l.expression()) + (l.negated() ? " NOT LIKE " : " LIKE ")
                    + renderExpr(l.pattern());
        }
        if (pred instanceof Not) {
            return "NOT (" + renderPredicate(((Not) pred).operand()) + ')';
        }
        if (pred instanceof And) {
            return ((And) pred).operands().stream()
                    .map(p -> '(' + renderPredicate(p) + ')')
                    .collect(Collectors.joining(" AND "));
        }
        if (pred instanceof Or) {
            return ((Or) pred).operands().stream()
                    .map(p -> '(' + renderPredicate(p) + ')')
                    .collect(Collectors.joining(" OR "));
        }
        throw new IllegalArgumentException("Unknown predicate type: " + pred.getClass().getName());
    }

    private String renderCase(CaseExpression c) {
        StringBuilder sb = new StringBuilder("CASE");
        for (CaseExpression.WhenClause w : c.whenClauses()) {
            sb.append(" WHEN ").append(renderExpr(w.condition()))
                    .append(" THEN ").append(renderExpr(w.result()));
        }
        if (c.elseExpr() != null) {
            sb.append(" ELSE ").append(renderExpr(c.elseExpr()));
        }
        sb.append(" END");
        return sb.toString();
    }

    // ── JOINS ──────────────────────────────────────────────────────────────────

    private String renderJoin(JoinClause j) {
        return renderJoinType(j.joinType()) + " JOIN `" + j.table().qualifiedName()
                + "` ON " + renderPredicate(j.condition());
    }

    private String renderJoinType(JoinClause.JoinType type) {
        switch (type) {
            case INNER: return "INNER";
            case LEFT: return "LEFT";
            case RIGHT: return "RIGHT";
            case FULL: return "FULL";
            case CROSS: return "CROSS";
            case LEFT_SEMI: return "LEFT SEMI";
            case RIGHT_SEMI: return "RIGHT SEMI";
            case LEFT_ANTI: return "LEFT ANTI";
            case RIGHT_ANTI: return "RIGHT ANTI";
            case LEFT_ANY: return "LEFT ANY";
            case RIGHT_ANY: return "RIGHT ANY";
            default: throw new IllegalArgumentException("Unknown join type: " + type);
        }
    }

    private String renderArrayJoin(ArrayJoinClause aj) {
        String cols = aj.arrays().stream().map(this::renderExpr).collect(Collectors.joining(", "));
        return (aj.isLeft() ? "LEFT " : "") + "ARRAY JOIN " + cols;
    }

    // ── ORDER BY ───────────────────────────────────────────────────────────────

    private String renderOrderSpec(OrderSpec o) {
        StringBuilder sb = new StringBuilder(renderExpr(o.expression()));
        sb.append(o.direction() == OrderSpec.Direction.DESC ? " DESC" : " ASC");
        if (o.nullsOrder() != null) {
            sb.append(o.nullsOrder() == OrderSpec.NullsOrder.FIRST ? " NULLS FIRST" : " NULLS LAST");
        }
        return sb.toString();
    }

    // ── GROUP MODIFIER ─────────────────────────────────────────────────────────

    private String renderGroupModifier(GroupModifier m) {
        switch (m) {
            case WITH_TOTALS: return "WITH TOTALS";
            case WITH_ROLLUP: return "WITH ROLLUP";
            case WITH_CUBE: return "WITH CUBE";
            default: throw new IllegalArgumentException("Unknown group modifier: " + m);
        }
    }

    // ── HELPERS ────────────────────────────────────────────────────────────────

    /**
     * Maps a Java value to its ClickHouse type string for parameter placeholders.
     */
    private static String clickHouseType(Object value) {
        if (value instanceof Long || value instanceof Integer
                || value instanceof Short || value instanceof Byte) {
            return "Int64";
        }
        if (value instanceof Double || value instanceof Float) {
            return "Float64";
        }
        if (value instanceof Boolean) {
            return "UInt8";
        }
        return "String";
    }
}

package io.blinkhouse.spring.repository;

import io.blinkhouse.core.exception.ChException;
import io.blinkhouse.core.metadata.EntityMetadata;
import io.blinkhouse.core.template.ChTemplate;
import io.blinkhouse.spring.support.SpringChExceptionTranslator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.QueryMethod;
import org.springframework.data.repository.query.RepositoryQuery;
import org.springframework.data.repository.query.parser.Part;
import org.springframework.data.repository.query.parser.PartTree;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Executes a derived repository method by translating a Spring Data {@link PartTree}
 * into a ClickHouse SQL query.
 *
 * <p>Supported keywords:
 * <ul>
 *   <li>{@code And} / {@code Or}</li>
 *   <li>{@code Between}, {@code LessThan}, {@code LessThanEqual},
 *       {@code GreaterThan}, {@code GreaterThanEqual}</li>
 *   <li>{@code In}, {@code NotIn}</li>
 *   <li>{@code Like}, {@code StartingWith}, {@code EndingWith}, {@code Containing}</li>
 *   <li>{@code IsNull}, {@code IsNotNull}</li>
 *   <li>{@code True}, {@code False}</li>
 *   <li>{@code OrderBy…Asc/Desc}</li>
 *   <li>{@code Top}/{@code First} (LIMIT)</li>
 *   <li>{@code Count}, {@code Exists}</li>
 * </ul>
 *
 * <p>{@code IgnoreCase} wraps the column in {@code lower()} and logs a WARN
 * because it defeats index usage in ClickHouse.
 *
 * <p>Validation at construction time (not first call):
 * <ul>
 *   <li>Unknown property names → {@link IllegalArgumentException}</li>
 *   <li>{@code IsNull} on non-nullable column → {@link IllegalArgumentException}</li>
 * </ul>
 */
public final class PartTreeChQuery implements RepositoryQuery {

    private static final Logger LOG = LoggerFactory.getLogger(PartTreeChQuery.class);

    private final QueryMethod queryMethod;
    private final ChTemplate template;
    private final EntityMetadata<?> entityMetadata;
    private final PartTree tree;
    private final String tableName;

    /**
     * Constructs a derived query for the given method.
     *
     * @param queryMethod    the Spring Data query method descriptor
     * @param template       the ChTemplate to execute against
     * @param entityMetadata resolved entity metadata
     */
    public PartTreeChQuery(QueryMethod queryMethod, ChTemplate template,
            EntityMetadata<?> entityMetadata) {
        this.queryMethod = queryMethod;
        this.template = template;
        this.entityMetadata = entityMetadata;
        this.tableName = entityMetadata.getQualifiedName();
        this.tree = new PartTree(queryMethod.getName(), entityMetadata.getJavaType());
        validateTree();
    }

    @Override
    public Object execute(Object[] parameters) {
        List<Object> bindValues = new ArrayList<>();
        String sql = buildSql(parameters, bindValues);
        try {
            if (tree.isCountProjection()) {
                List<?> result = template.queryForList(Long.class, sql);
                return result.isEmpty() ? 0L : result.get(0);
            }
            if (tree.isExistsProjection()) {
                List<?> result = template.queryForList(Long.class, sql);
                return !result.isEmpty() && (Long) result.get(0) > 0;
            }
            return template.queryForList(entityMetadata.getJavaType(), sql);
        } catch (ChException ex) {
            throw SpringChExceptionTranslator.translate(ex);
        }
    }

    @Override
    public QueryMethod getQueryMethod() {
        return queryMethod;
    }

    private String buildSql(Object[] parameters, List<Object> bindValues) {
        StringBuilder sb = new StringBuilder();

        if (tree.isCountProjection()) {
            sb.append("SELECT count() FROM ").append(tableName);
        } else if (tree.isExistsProjection()) {
            sb.append("SELECT count() FROM ").append(tableName);
        } else {
            sb.append("SELECT * FROM ").append(tableName);
        }

        // WHERE clause
        int[] paramIdx = { 0 };
        List<String> orClauses = new ArrayList<>();
        for (PartTree.OrPart orPart : tree) {
            List<String> andClauses = new ArrayList<>();
            for (Part part : orPart) {
                andClauses.add(renderPart(part, parameters, paramIdx));
            }
            if (!andClauses.isEmpty()) {
                orClauses.add(String.join(" AND ", andClauses));
            }
        }
        if (!orClauses.isEmpty()) {
            sb.append(" WHERE ");
            if (orClauses.size() == 1) {
                sb.append(orClauses.get(0));
            } else {
                sb.append("(").append(String.join(") OR (", orClauses)).append(")");
            }
        }

        // ORDER BY
        Sort sort = tree.getSort();
        if (sort != null && sort.isSorted()) {
            sb.append(" ORDER BY ");
            StringJoiner sj = new StringJoiner(", ");
            for (Sort.Order order : sort) {
                sj.add(quoteCol(order.getProperty()) + " " + order.getDirection().name());
            }
            sb.append(sj);
        }

        // LIMIT
        if (tree.isLimiting()) {
            sb.append(" LIMIT ").append(tree.getMaxResults());
        }

        // Pageable parameter
        for (Object param : parameters) {
            if (param instanceof Pageable) {
                Pageable pageable = (Pageable) param;
                if (pageable.isPaged()) {
                    sb.append(" LIMIT ").append(pageable.getPageSize());
                    long offset = pageable.getOffset();
                    if (offset > 0) {
                        sb.append(" OFFSET ").append(offset);
                        LOG.warn("Offset pagination on {} offset={} — prefer keyset (Cursor) "
                            + "for large datasets", tableName, offset);
                    }
                }
            }
        }

        return sb.toString();
    }

    private String renderPart(Part part, Object[] parameters, int[] paramIdx) {
        String col = columnName(part.getProperty().getSegment());
        boolean ignoreCase = part.shouldIgnoreCase() != Part.IgnoreCaseType.NEVER;
        if (ignoreCase) {
            LOG.warn("IgnoreCase on column {} defeats ClickHouse index — avoid in hot paths", col);
            col = "lower(" + col + ")";
        }

        Part.Type type = part.getType();
        switch (type) {
            case SIMPLE_PROPERTY:
                return col + " = " + toSqlLiteral(parameters[paramIdx[0]++], ignoreCase);
            case NEGATING_SIMPLE_PROPERTY:
                return col + " != " + toSqlLiteral(parameters[paramIdx[0]++], ignoreCase);
            case LESS_THAN:
                return col + " < " + toSqlLiteral(parameters[paramIdx[0]++], false);
            case LESS_THAN_EQUAL:
                return col + " <= " + toSqlLiteral(parameters[paramIdx[0]++], false);
            case GREATER_THAN:
                return col + " > " + toSqlLiteral(parameters[paramIdx[0]++], false);
            case GREATER_THAN_EQUAL:
                return col + " >= " + toSqlLiteral(parameters[paramIdx[0]++], false);
            case BETWEEN:
                String lo = toSqlLiteral(parameters[paramIdx[0]++], false);
                String hi = toSqlLiteral(parameters[paramIdx[0]++], false);
                return col + " BETWEEN " + lo + " AND " + hi;
            case IN: {
                Object collection = parameters[paramIdx[0]++];
                return col + " IN (" + inLiterals(collection) + ")";
            }
            case NOT_IN: {
                Object collection = parameters[paramIdx[0]++];
                return col + " NOT IN (" + inLiterals(collection) + ")";
            }
            case LIKE:
                return col + " LIKE " + toSqlLiteral(parameters[paramIdx[0]++], ignoreCase);
            case STARTING_WITH: {
                String prefix = String.valueOf(parameters[paramIdx[0]++]);
                return col + " LIKE '" + escape(prefix) + "%'";
            }
            case ENDING_WITH: {
                String suffix = String.valueOf(parameters[paramIdx[0]++]);
                return col + " LIKE '%" + escape(suffix) + "'";
            }
            case CONTAINING: {
                String fragment = String.valueOf(parameters[paramIdx[0]++]);
                return col + " LIKE '%" + escape(fragment) + "%'";
            }
            case IS_NULL:
                return col + " IS NULL";
            case IS_NOT_NULL:
                return col + " IS NOT NULL";
            case TRUE:
                return col + " = 1";
            case FALSE:
                return col + " = 0";
            case EXISTS:
                return "1 = 1";
            default:
                throw new IllegalArgumentException("Unsupported PartTree keyword: " + type);
        }
    }

    private String columnName(String propertyName) {
        // look up in entity metadata for accurate snake_case name
        return entityMetadata.getColumns().stream()
            .filter(c -> c.getJavaName().equals(propertyName))
            .findFirst()
            .map(c -> "`" + c.getName() + "`")
            .orElse("`" + toSnakeCase(propertyName) + "`");
    }

    private String quoteCol(String col) {
        return "`" + col + "`";
    }

    private String toSnakeCase(String name) {
        return name.replaceAll("([A-Z])", "_$1").toLowerCase().replaceAll("^_", "");
    }

    private String toSqlLiteral(Object value, boolean lowerCase) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Number) {
            return value.toString();
        }
        if (value instanceof Boolean) {
            return (Boolean) value ? "1" : "0";
        }
        String str = value.toString().replace("'", "\\'");
        if (lowerCase) {
            str = str.toLowerCase();
        }
        return "'" + str + "'";
    }

    private String inLiterals(Object collection) {
        if (collection instanceof Iterable) {
            StringJoiner sj = new StringJoiner(", ");
            for (Object item : (Iterable<?>) collection) {
                sj.add(toSqlLiteral(item, false));
            }
            return sj.toString();
        }
        return toSqlLiteral(collection, false);
    }

    private String escape(String s) {
        return s.replace("'", "\\'").replace("%", "\\%").replace("_", "\\_");
    }

    private void validateTree() {
        for (PartTree.OrPart orPart : tree) {
            for (Part part : orPart) {
                String property = part.getProperty().getSegment();
                boolean found = entityMetadata.getColumns().stream()
                    .anyMatch(c -> c.getJavaName().equals(property));
                if (!found) {
                    throw new IllegalArgumentException(
                        "Property '" + property + "' not found on "
                        + entityMetadata.getJavaType().getSimpleName()
                        + " — check your repository method name");
                }
            }
        }
    }
}

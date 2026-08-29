package io.blinkhouse.spring.repository;

import io.blinkhouse.core.exception.ChException;
import io.blinkhouse.core.template.ChTemplate;
import io.blinkhouse.spring.support.SpringChExceptionTranslator;
import org.springframework.data.repository.query.QueryMethod;
import org.springframework.data.repository.query.RepositoryQuery;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Executes a {@link Query @Query}-annotated repository method as a native ClickHouse SQL query.
 *
 * <p>Parameter binding supports:
 * <ul>
 *   <li>Named parameters: {@code :paramName} matched to method parameters by
 *       {@link org.springframework.data.repository.query.Param @Param} or parameter name.</li>
 *   <li>Positional parameters: {@code ?1}, {@code ?2}, … matched by position.</li>
 * </ul>
 */
public final class NativeChQuery implements RepositoryQuery {

    private static final Pattern NAMED_PARAM = Pattern.compile(":([A-Za-z][A-Za-z0-9_]*)");
    private static final Pattern POSITIONAL_PARAM = Pattern.compile("\\?(\\d+)");

    private final QueryMethod queryMethod;
    private final Method method;
    private final ChTemplate template;
    private final String sql;
    private final Class<?> domainType;
    private final Class<?> returnElementType;

    /**
     * Constructs a native query for the given method.
     *
     * @param queryMethod the Spring Data query method descriptor
     * @param method      the actual Java method
     * @param template    the ChTemplate to execute against
     * @param sql         the raw SQL from {@link Query#value()}
     * @param domainType  the repository's entity type
     */
    public NativeChQuery(QueryMethod queryMethod, Method method, ChTemplate template,
            String sql, Class<?> domainType) {
        this.queryMethod = queryMethod;
        this.method = method;
        this.template = template;
        this.sql = sql;
        this.domainType = domainType;
        this.returnElementType = resolveReturnElementType(method);
    }

    @Override
    public Object execute(Object[] parameters) {
        String boundSql = bindParameters(sql, method, parameters);
        try {
            if (method.getReturnType() == long.class || method.getReturnType() == Long.class) {
                List<Long> result = template.queryForList(Long.class, boundSql);
                return result.isEmpty() ? 0L : result.get(0);
            }
            return template.queryForList(returnElementType, boundSql);
        } catch (ChException ex) {
            throw SpringChExceptionTranslator.translate(ex);
        }
    }

    @Override
    public QueryMethod getQueryMethod() {
        return queryMethod;
    }

    private String bindParameters(String rawSql, Method javaMethod, Object[] args) {
        String result = rawSql;
        java.lang.reflect.Parameter[] params = javaMethod.getParameters();

        // Named parameters: :name
        StringBuffer sb = new StringBuffer();
        Matcher named = NAMED_PARAM.matcher(result);
        while (named.find()) {
            String name = named.group(1);
            Object value = findByName(params, args, name);
            named.appendReplacement(sb, Matcher.quoteReplacement(toSqlLiteral(value)));
        }
        named.appendTail(sb);
        result = sb.toString();

        // Positional parameters: ?1, ?2
        sb = new StringBuffer();
        Matcher positional = POSITIONAL_PARAM.matcher(result);
        while (positional.find()) {
            int idx = Integer.parseInt(positional.group(1)) - 1;
            Object value = idx < args.length ? args[idx] : null;
            positional.appendReplacement(sb, Matcher.quoteReplacement(toSqlLiteral(value)));
        }
        positional.appendTail(sb);
        return sb.toString();
    }

    private Object findByName(java.lang.reflect.Parameter[] params, Object[] args, String name) {
        for (int i = 0; i < params.length; i++) {
            org.springframework.data.repository.query.Param p =
                params[i].getAnnotation(org.springframework.data.repository.query.Param.class);
            if (p != null && name.equals(p.value())) {
                return args[i];
            }
            if (name.equals(params[i].getName())) {
                return args[i];
            }
        }
        throw new IllegalArgumentException("No parameter named '" + name + "' found");
    }

    private String toSqlLiteral(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Number) {
            return value.toString();
        }
        if (value instanceof Boolean) {
            return (Boolean) value ? "1" : "0";
        }
        return "'" + value.toString().replace("'", "\\'") + "'";
    }

    private Class<?> resolveReturnElementType(Method javaMethod) {
        Type returnType = javaMethod.getGenericReturnType();
        if (returnType instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) returnType;
            Type[] typeArgs = pt.getActualTypeArguments();
            if (typeArgs.length > 0 && typeArgs[0] instanceof Class) {
                return (Class<?>) typeArgs[0];
            }
        }
        Class<?> raw = javaMethod.getReturnType();
        if (raw == List.class || raw == Iterable.class) {
            return domainType;
        }
        return raw;
    }
}

package io.blinkhouse.core.metadata;

import io.blinkhouse.core.annotation.ChColumn;
import io.blinkhouse.core.annotation.ChIgnore;
import io.blinkhouse.core.annotation.ChTable;
import io.blinkhouse.core.type.TypeHandler;
import io.blinkhouse.core.type.TypeRegistry;

import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;

/**
 * Reflectively resolves {@link EntityMetadata} from a class annotated with {@link ChTable}.
 *
 * <p>This is the Phase 2 bootstrap resolver. Phase 1 will replace the inner loop with
 * {@code LambdaMetafactory}-backed {@link ValueAccessor}s and a full {@code ClickHouseType}
 * parser. For now, accessor uses plain {@link Field#get} (fast enough for test volumes).
 *
 * <p>Failure messages are intentionally specific — they name the class, field, and the fix.
 */
public final class EntityMetadataFactory {

    private final TypeRegistry typeRegistry;

    public EntityMetadataFactory(TypeRegistry typeRegistry) {
        this.typeRegistry = typeRegistry;
    }

    /**
     * Resolves metadata for {@code entityClass}.
     *
     * @throws IllegalArgumentException if {@code @ChTable} is absent or a column cannot be mapped
     */
    public <T> EntityMetadata<T> resolve(Class<T> entityClass) {
        ChTable tableAnn = entityClass.getAnnotation(ChTable.class);
        if (tableAnn == null) {
            throw new IllegalArgumentException(
                    "ClickORM: " + entityClass.getName() + " is not annotated with @ChTable");
        }

        String tableName = tableAnn.name().isEmpty()
                ? toSnakeCase(entityClass.getSimpleName())
                : tableAnn.name();
        String database = tableAnn.database();

        List<ColumnMetadata<T>> columns = new ArrayList<>();

        if (entityClass.isRecord()) {
            for (RecordComponent rc : entityClass.getRecordComponents()) {
                if (rc.isAnnotationPresent(ChIgnore.class)) {
                    continue;
                }
                columns.add(buildColumn(entityClass, rc));
            }
        } else {
            for (Field field : entityClass.getDeclaredFields()) {
                if (field.isAnnotationPresent(ChIgnore.class)) {
                    continue;
                }
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                columns.add(buildColumnFromField(entityClass, field));
            }
        }

        return new EntityMetadata<>(entityClass, database, tableName, columns);
    }

    private <T> ColumnMetadata<T> buildColumn(Class<T> entityClass, RecordComponent rc) {
        ChColumn ann = rc.getAnnotation(ChColumn.class);
        String colName = ann != null && !ann.name().isEmpty()
                ? ann.name() : toSnakeCase(rc.getName());
        String chTypeName = ann != null && !ann.type().isEmpty()
                ? ann.type() : inferChTypeName(entityClass, rc.getName(), rc.getType());
        boolean insertable = ann == null
                || !ann.materialized() && !ann.alias() && !ann.ephemeral();

        TypeHandler<?> handler = typeRegistry.lookup(chTypeName);

        // Reflective accessor for Phase 2; replaced by LambdaMetafactory in Phase 1
        Field backingField;
        try {
            backingField = entityClass.getDeclaredField(rc.getName());
            backingField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException(
                    "ClickORM: cannot access backing field for record component '"
                    + rc.getName() + "' in " + entityClass.getName(), e);
        }
        final Field f = backingField;
        ValueAccessor<T> accessor = entity -> {
            try {
                return f.get(entity);
            } catch (IllegalAccessException ex) {
                throw new RuntimeException("ClickORM: field access failed for " + f.getName(), ex);
            }
        };

        return new ColumnMetadata<>(colName, rc.getName(), handler, accessor, insertable);
    }

    private <T> ColumnMetadata<T> buildColumnFromField(Class<T> entityClass, Field field) {
        field.setAccessible(true);
        ChColumn ann = field.getAnnotation(ChColumn.class);
        String colName = ann != null && !ann.name().isEmpty()
                ? ann.name() : toSnakeCase(field.getName());
        String chTypeName = ann != null && !ann.type().isEmpty()
                ? ann.type() : inferChTypeName(entityClass, field.getName(), field.getType());
        boolean insertable = ann == null
                || !ann.materialized() && !ann.alias() && !ann.ephemeral();

        TypeHandler<?> handler = typeRegistry.lookup(chTypeName);

        ValueAccessor<T> accessor = entity -> {
            try {
                return field.get(entity);
            } catch (IllegalAccessException ex) {
                throw new RuntimeException("ClickORM: field access failed for " + field.getName(), ex);
            }
        };

        return new ColumnMetadata<>(colName, field.getName(), handler, accessor, insertable);
    }

    private String inferChTypeName(Class<?> entityClass, String fieldName, Class<?> javaType) {
        if (javaType == long.class || javaType == Long.class) {
            return "UInt64";
        }
        if (javaType == java.util.UUID.class) {
            return "UUID";
        }
        if (javaType == java.time.Instant.class) {
            return "DateTime64(9,'UTC')";
        }
        if (javaType == java.math.BigDecimal.class) {
            return "Decimal(38,9)";
        }
        if (javaType == java.math.BigInteger.class) {
            return "Int256";
        }
        if (javaType == java.net.Inet6Address.class) {
            return "IPv6";
        }
        throw new IllegalArgumentException(
                "ClickORM: cannot infer ClickHouse type for " + entityClass.getSimpleName()
                + "." + fieldName + " (" + javaType.getName() + "). "
                + "Use @ChColumn(type=\"...\") to specify an explicit ClickHouse type.");
    }

    static String toSnakeCase(String name) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                sb.append('_');
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }
}

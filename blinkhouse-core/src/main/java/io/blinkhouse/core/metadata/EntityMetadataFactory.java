package io.blinkhouse.core.metadata;

import io.blinkhouse.core.annotation.ChCodec;
import io.blinkhouse.core.annotation.ChColumn;
import io.blinkhouse.core.annotation.ChEngine;
import io.blinkhouse.core.annotation.ChIgnore;
import io.blinkhouse.core.annotation.ChSkipIndex;
import io.blinkhouse.core.annotation.ChSkipIndexes;
import io.blinkhouse.core.annotation.ChTable;
import io.blinkhouse.core.annotation.Engine;
import io.blinkhouse.core.exception.ChMappingException;
import io.blinkhouse.core.type.TypeHandler;
import io.blinkhouse.core.type.TypeRegistry;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.Inet6Address;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Produces {@link EntityMetadata} from a Java class via reflection.
 *
 * <p>Resolution is performed once per class; callers should cache the result.
 * The {@link TypeRegistry} is used to look up a {@link TypeHandler} for each column.
 */
public final class EntityMetadataFactory {

    private final TypeRegistry registry;

    /** Constructs with the type registry to use for handler resolution. */
    public EntityMetadataFactory(TypeRegistry registry) {
        this.registry = registry;
    }

    /**
     * Resolves {@link EntityMetadata} for the given entity class.
     *
     * @param <T>         the entity type
     * @param entityClass the class to inspect
     * @return resolved metadata
     * @throws ChMappingException if the class is not annotated or a column cannot be mapped
     */
    @SuppressWarnings("unchecked")
    public <T> EntityMetadata<T> resolve(Class<T> entityClass) {
        ChTable tableAnn = entityClass.getAnnotation(ChTable.class);
        if (tableAnn == null) {
            throw new ChMappingException(
                "ClickORM: " + entityClass.getSimpleName() + " is not annotated with @ChTable");
        }

        String table = tableAnn.name().isEmpty()
            ? toSnakeCase(entityClass.getSimpleName())
            : tableAnn.name();
        String database = tableAnn.database();

        EngineMetadata engineMeta = resolveEngine(entityClass, tableAnn);

        List<MemberInfo> members = resolveMembers(entityClass);
        List<ColumnMetadata<T>> columns = new ArrayList<>();
        for (MemberInfo m : members) {
            columns.add(resolveColumn(entityClass, m));
        }

        List<ColumnMetadata<T>> insertable = new ArrayList<>();
        for (ColumnMetadata<T> col : columns) {
            if (col.isInsertable()) {
                insertable.add(col);
            }
        }

        List<SkipIndexMetadata> skipIndexes = resolveSkipIndexes(entityClass);

        Optional<String> ttl = tableAnn.ttl().isEmpty()
            ? Optional.empty()
            : Optional.of(tableAnn.ttl());

        Map<String, String> settings = new HashMap<>();
        for (io.blinkhouse.core.annotation.ChSetting s : tableAnn.settings()) {
            settings.put(s.name(), s.value());
        }

        Optional<String> onCluster = tableAnn.onCluster().isEmpty()
            ? Optional.empty()
            : Optional.of(tableAnn.onCluster());

        return new EntityMetadata<>(
            entityClass,
            database,
            table,
            engineMeta,
            columns,
            insertable,
            Arrays.asList(tableAnn.orderBy()),
            Arrays.asList(tableAnn.partitionBy()),
            resolvePrimaryKey(tableAnn),
            ttl,
            settings,
            skipIndexes,
            onCluster
        );
    }

    private List<String> resolvePrimaryKey(ChTable tableAnn) {
        if (tableAnn.primaryKey().length > 0) {
            return Arrays.asList(tableAnn.primaryKey());
        }
        return Arrays.asList(tableAnn.orderBy());
    }

    private <T> ColumnMetadata<T> resolveColumn(Class<T> entityClass, MemberInfo m) {
        ChColumn colAnn = m.columnAnn;
        String name = (colAnn != null && !colAnn.name().isEmpty())
            ? colAnn.name()
            : toSnakeCase(m.javaName);

        boolean nullable = colAnn != null && colAnn.nullable();
        boolean materialized = colAnn != null && !colAnn.materialized().isEmpty();
        boolean alias = colAnn != null && !colAnn.alias().isEmpty();
        boolean ephemeral = colAnn != null && colAnn.ephemeral();
        boolean insertable = !materialized && !alias && !ephemeral;

        String chTypeName;
        if (colAnn != null && !colAnn.type().isEmpty()) {
            chTypeName = colAnn.type();
        } else {
            chTypeName = inferChTypeName(m.javaType, nullable);
        }

        TypeHandler<?> handler = registry.find(chTypeName)
            .orElseGet(() -> registry.findByJavaType(m.javaType)
                .orElseThrow(() -> new ChMappingException(
                    "ClickORM: cannot map " + entityClass.getSimpleName() + "." + m.javaName
                    + " (" + m.javaType.getName() + ")\n"
                    + "  reason : no TypeHandler registered for '" + chTypeName + "'\n"
                    + "  fix    : add @ChColumn(type=\"...\") or register a custom TypeHandler")));

        final MemberInfo captured = m;
        ValueAccessor<T> accessor = entity -> {
            try {
                captured.field.setAccessible(true);
                return captured.field.get(entity);
            } catch (IllegalAccessException e) {
                throw new ChMappingException("Cannot access field " + captured.javaName + ": " + e.getMessage());
            }
        };

        List<String> codecs = new ArrayList<>();
        if (m.codecAnn != null) {
            codecs.addAll(Arrays.asList(m.codecAnn.value()));
        }

        Optional<String> defaultExpr = (colAnn != null && !colAnn.defaultExpression().isEmpty())
            ? Optional.of(colAnn.defaultExpression())
            : Optional.empty();
        Optional<String> colTtl = (colAnn != null && !colAnn.ttl().isEmpty())
            ? Optional.of(colAnn.ttl())
            : Optional.empty();
        Optional<String> comment = (colAnn != null && !colAnn.comment().isEmpty())
            ? Optional.of(colAnn.comment())
            : Optional.empty();

        return new ColumnMetadata<>(
            name, m.javaName, m.javaType, chTypeName, handler, accessor,
            nullable, materialized, alias, ephemeral, insertable,
            defaultExpr, codecs, colTtl, comment
        );
    }

    private String inferChTypeName(Class<?> javaType, boolean nullable) {
        String base;
        if (javaType == long.class || javaType == Long.class) {
            base = "UInt64";
        } else if (javaType == int.class || javaType == Integer.class) {
            base = "Int32";
        } else if (javaType == short.class || javaType == Short.class) {
            base = "Int16";
        } else if (javaType == byte.class || javaType == Byte.class) {
            base = "Int8";
        } else if (javaType == boolean.class || javaType == Boolean.class) {
            base = "Bool";
        } else if (javaType == float.class || javaType == Float.class) {
            base = "Float32";
        } else if (javaType == double.class || javaType == Double.class) {
            base = "Float64";
        } else if (javaType == String.class) {
            base = "String";
        } else if (javaType == UUID.class) {
            base = "UUID";
        } else if (javaType == Instant.class) {
            base = "DateTime64(9,'UTC')";
        } else if (javaType == LocalDate.class) {
            base = "Date";
        } else if (javaType == BigDecimal.class) {
            base = "Decimal(38,9)";
        } else if (javaType == BigInteger.class) {
            base = "Int256";
        } else if (javaType == Inet6Address.class) {
            base = "IPv6";
        } else {
            base = "String";
        }
        if (nullable) {
            return "Nullable(" + base + ")";
        }
        return base;
    }

    private List<MemberInfo> resolveMembers(Class<?> entityClass) {
        List<MemberInfo> result = new ArrayList<>();
        if (entityClass.isRecord()) {
            for (RecordComponent rc : entityClass.getRecordComponents()) {
                if (rc.isAnnotationPresent(ChIgnore.class)) {
                    continue;
                }
                Field field = null;
                try {
                    field = entityClass.getDeclaredField(rc.getName());
                } catch (NoSuchFieldException e) {
                    throw new ChMappingException("Cannot find backing field for record component " + rc.getName());
                }
                result.add(new MemberInfo(
                    rc.getName(),
                    rc.getType(),
                    field,
                    rc.getAnnotation(ChColumn.class),
                    rc.getAnnotation(ChCodec.class)
                ));
            }
        } else {
            for (Field f : entityClass.getDeclaredFields()) {
                if (f.isAnnotationPresent(ChIgnore.class)) {
                    continue;
                }
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                if (java.lang.reflect.Modifier.isTransient(f.getModifiers())) {
                    continue;
                }
                result.add(new MemberInfo(
                    f.getName(),
                    f.getType(),
                    f,
                    f.getAnnotation(ChColumn.class),
                    f.getAnnotation(ChCodec.class)
                ));
            }
        }
        return result;
    }

    private EngineMetadata resolveEngine(Class<?> entityClass, ChTable tableAnn) {
        ChEngine ann = entityClass.getAnnotation(ChEngine.class);
        Engine engine = (ann != null) ? ann.value() : Engine.MERGE_TREE;

        Optional<String> versionColumn = Optional.empty();
        Optional<String> isDeletedColumn = Optional.empty();
        List<String> summingColumns = new ArrayList<>();
        Optional<String> signColumn = Optional.empty();
        Optional<String> versionCollapsingColumn = Optional.empty();
        boolean replicated = false;
        String zkPath = "/clickhouse/tables/{shard}/{database}/{table}";
        String replica = "{replica}";
        Optional<String> cluster = Optional.empty();
        Optional<String> localTable = Optional.empty();
        Optional<String> shardingKey = Optional.empty();

        if (ann != null) {
            if (!ann.versionColumn().isEmpty()) {
                versionColumn = Optional.of(ann.versionColumn());
            }
            if (!ann.isDeletedColumn().isEmpty()) {
                isDeletedColumn = Optional.of(ann.isDeletedColumn());
            }
            summingColumns = Arrays.asList(ann.summingColumns());
            if (!ann.signColumn().isEmpty()) {
                signColumn = Optional.of(ann.signColumn());
            }
            if (!ann.versionCollapsingColumn().isEmpty()) {
                versionCollapsingColumn = Optional.of(ann.versionCollapsingColumn());
            }
            replicated = ann.replicated();
            zkPath = ann.zkPath();
            replica = ann.replica();
            if (!ann.cluster().isEmpty()) {
                cluster = Optional.of(ann.cluster());
            }
            if (!ann.localTable().isEmpty()) {
                localTable = Optional.of(ann.localTable());
            }
            if (!ann.shardingKey().isEmpty()) {
                shardingKey = Optional.of(ann.shardingKey());
            }
        }

        return new EngineMetadata(
            engine, versionColumn, isDeletedColumn, summingColumns,
            signColumn, versionCollapsingColumn, replicated, zkPath, replica,
            cluster, localTable, shardingKey
        );
    }

    private List<SkipIndexMetadata> resolveSkipIndexes(Class<?> entityClass) {
        List<SkipIndexMetadata> result = new ArrayList<>();
        ChSkipIndex single = entityClass.getAnnotation(ChSkipIndex.class);
        if (single != null) {
            result.add(toSkipIndex(single));
        }
        ChSkipIndexes container = entityClass.getAnnotation(ChSkipIndexes.class);
        if (container != null) {
            for (ChSkipIndex idx : container.value()) {
                result.add(toSkipIndex(idx));
            }
        }
        return result;
    }

    private SkipIndexMetadata toSkipIndex(ChSkipIndex ann) {
        return new SkipIndexMetadata(
            ann.name(),
            ann.expression(),
            ann.type(),
            ann.granularity(),
            Arrays.asList(ann.params())
        );
    }

    /** Converts camelCase or PascalCase to snake_case. */
    public static String toSnakeCase(String name) {
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

    private static final class MemberInfo {
        final String javaName;
        final Class<?> javaType;
        final Field field;
        final ChColumn columnAnn;
        final ChCodec codecAnn;

        MemberInfo(String javaName, Class<?> javaType, Field field, ChColumn columnAnn, ChCodec codecAnn) {
            this.javaName = javaName;
            this.javaType = javaType;
            this.field = field;
            this.columnAnn = columnAnn;
            this.codecAnn = codecAnn;
        }
    }
}

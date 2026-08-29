package io.blinkhouse.core.metadata;

import io.blinkhouse.core.annotation.ChDictionary;
import io.blinkhouse.core.annotation.ChDictionaryKey;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Builds {@link DictionaryMetadata} from a {@link ChDictionary}-annotated class.
 *
 * <p>Fields annotated with {@link ChDictionaryKey} are treated as key attributes.
 * All other fields become non-key attributes. Java type → ClickHouse type mapping uses
 * simple heuristics; use {@link io.blinkhouse.core.annotation.ChColumn} on each field
 * to override with an explicit type string.
 */
public final class DictionaryMetadataFactory {

    private DictionaryMetadataFactory() {}

    /**
     * Resolves dictionary metadata from the annotated class.
     *
     * @param cls the class annotated with {@link ChDictionary}
     * @return the resolved metadata
     * @throws IllegalArgumentException if the class is not annotated or has no key field
     */
    public static DictionaryMetadata resolve(Class<?> cls) {
        ChDictionary ann = cls.getAnnotation(ChDictionary.class);
        if (ann == null) {
            throw new IllegalArgumentException(
                cls.getName() + " is not annotated with @ChDictionary");
        }

        String name = ann.name().isBlank()
                ? toSnakeCase(cls.getSimpleName())
                : ann.name();
        String db = ann.database();
        Optional<String> onCluster = ann.onCluster().isBlank()
                ? Optional.empty()
                : Optional.of(ann.onCluster());

        List<DictionaryAttributeMetadata> attributes = new ArrayList<>();
        boolean hasKey = false;

        for (Field field : cls.getDeclaredFields()) {
            boolean isKey = field.isAnnotationPresent(ChDictionaryKey.class);
            if (isKey) {
                hasKey = true;
            }
            String chType = resolveChType(field);
            attributes.add(new DictionaryAttributeMetadata(
                field.getName(), chType, isKey, ""));
        }

        if (!hasKey) {
            throw new IllegalArgumentException(
                cls.getName() + " has no field annotated with @ChDictionaryKey");
        }

        return new DictionaryMetadata(
            db, name,
            ann.sourceType(), ann.sourceTable(), ann.sourceWhere(),
            ann.layout(),
            ann.lifetimeSeconds(), ann.lifetimeMaxSeconds(),
            attributes, onCluster);
    }

    private static String resolveChType(Field field) {
        io.blinkhouse.core.annotation.ChColumn col =
            field.getAnnotation(io.blinkhouse.core.annotation.ChColumn.class);
        if (col != null && !col.type().isBlank()) {
            return col.type();
        }
        Class<?> t = field.getType();
        if (t == long.class || t == Long.class) return "UInt64";
        if (t == int.class || t == Integer.class) return "UInt32";
        if (t == short.class || t == Short.class) return "UInt16";
        if (t == byte.class || t == Byte.class) return "UInt8";
        if (t == double.class || t == Double.class) return "Float64";
        if (t == float.class || t == Float.class) return "Float32";
        if (t == boolean.class || t == Boolean.class) return "UInt8";
        if (t == java.math.BigDecimal.class) return "Decimal(18, 4)";
        return "String";
    }

    private static String toSnakeCase(String camel) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < camel.length(); i++) {
            char c = camel.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                sb.append('_');
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }
}

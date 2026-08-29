package io.blinkhouse.core.metadata;

import io.blinkhouse.core.annotation.ChMaterializedView;

import java.util.Optional;

/**
 * Builds {@link MaterializedViewMetadata} from a {@link ChMaterializedView}-annotated class.
 */
public final class MaterializedViewMetadataFactory {

    private MaterializedViewMetadataFactory() {}

    /**
     * Resolves metadata from the annotated class.
     *
     * @param cls the class annotated with {@link ChMaterializedView}
     * @return the resolved metadata
     * @throws IllegalArgumentException if the class is not annotated
     */
    public static MaterializedViewMetadata resolve(Class<?> cls) {
        ChMaterializedView ann = cls.getAnnotation(ChMaterializedView.class);
        if (ann == null) {
            throw new IllegalArgumentException(
                cls.getName() + " is not annotated with @ChMaterializedView");
        }
        String name = ann.name().isBlank()
                ? toSnakeCase(cls.getSimpleName())
                : ann.name();
        String db = ann.database();
        String target = ann.targetTable();
        String sql = ann.selectSql();
        boolean populate = ann.populate();
        Optional<String> onCluster = ann.onCluster().isBlank()
                ? Optional.empty()
                : Optional.of(ann.onCluster());
        return new MaterializedViewMetadata(db, name, target, sql, populate, onCluster);
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

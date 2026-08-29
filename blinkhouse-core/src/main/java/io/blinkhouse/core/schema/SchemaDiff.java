package io.blinkhouse.core.schema;

import io.blinkhouse.core.metadata.ColumnMetadata;
import io.blinkhouse.core.metadata.EntityMetadata;
import io.blinkhouse.core.metadata.SkipIndexMetadata;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Compares an {@link EntityMetadata} definition against a {@link LiveTable} and produces
 * a list of {@link SchemaChange} records describing the drift.
 *
 * <p>An empty result list means the live schema exactly matches the entity definition.
 */
public final class SchemaDiff {

    private SchemaDiff() {}

    /**
     * Computes the schema changes required to bring {@code live} in line with {@code entity}.
     *
     * @param entity the desired schema as declared in annotations
     * @param live   the current live state of the table
     * @return ordered list of changes; empty if schemas match
     */
    public static List<SchemaChange> diff(EntityMetadata<?> entity, LiveTable live) {
        List<SchemaChange> changes = new ArrayList<>();

        diffEngine(entity, live, changes);
        diffOrderBy(entity, live, changes);
        diffTtl(entity, live, changes);
        diffColumns(entity, live, changes);
        diffIndexes(entity, live, changes);

        return changes;
    }

    private static void diffEngine(EntityMetadata<?> entity, LiveTable live, List<SchemaChange> changes) {
        String expectedEngine = resolveEngineName(entity);
        String actualEngine = normaliseEngine(live.getEngine());
        if (!expectedEngine.equalsIgnoreCase(actualEngine)) {
            changes.add(new SchemaChange.EngineMismatch(expectedEngine, actualEngine));
        }
    }

    private static void diffOrderBy(EntityMetadata<?> entity, LiveTable live, List<SchemaChange> changes) {
        List<String> expected = entity.getOrderBy();
        List<String> actual = live.getOrderBy();
        if (!expected.equals(actual)) {
            changes.add(new SchemaChange.OrderByMismatch(expected, actual));
        }
    }

    private static void diffTtl(EntityMetadata<?> entity, LiveTable live, List<SchemaChange> changes) {
        String expectedTtl = entity.getTtl().orElse("");
        String actualTtl = live.getTtl().orElse("");
        if (!expectedTtl.equals(actualTtl)) {
            if (!expectedTtl.isEmpty() || !actualTtl.isEmpty()) {
                changes.add(new SchemaChange.TtlMismatch(expectedTtl, actualTtl));
            }
        }
    }

    private static void diffColumns(EntityMetadata<?> entity, LiveTable live, List<SchemaChange> changes) {
        Map<String, LiveColumn> liveByName = new HashMap<>();
        for (LiveColumn col : live.getColumns()) {
            liveByName.put(col.getName(), col);
        }

        Map<String, ColumnMetadata<?>> entityByName = new HashMap<>();
        for (ColumnMetadata<?> col : entity.getColumns()) {
            entityByName.put(col.getName(), col);
        }

        String previousCol = "";
        for (ColumnMetadata<?> entityCol : entity.getColumns()) {
            LiveColumn liveCol = liveByName.get(entityCol.getName());
            if (liveCol == null) {
                changes.add(new SchemaChange.AddColumn(entityCol.getName(), entityCol.getChTypeName(), previousCol));
            } else {
                String entityType = normaliseType(entityCol.getChTypeName());
                String liveType = normaliseType(liveCol.getType());
                if (!entityType.equals(liveType)) {
                    changes.add(new SchemaChange.ModifyColumnType(entityCol.getName(), liveType, entityType));
                }
            }
            previousCol = entityCol.getName();
        }

        for (LiveColumn liveCol : live.getColumns()) {
            if (!entityByName.containsKey(liveCol.getName())) {
                changes.add(new SchemaChange.DropColumn(liveCol.getName()));
            }
        }
    }

    private static void diffIndexes(EntityMetadata<?> entity, LiveTable live, List<SchemaChange> changes) {
        Map<String, LiveIndex> liveByName = new HashMap<>();
        for (LiveIndex idx : live.getIndexes()) {
            liveByName.put(idx.getName(), idx);
        }
        Map<String, SkipIndexMetadata> entityByName = new HashMap<>();
        for (SkipIndexMetadata idx : entity.getSkipIndexes()) {
            entityByName.put(idx.getName(), idx);
        }

        for (SkipIndexMetadata entityIdx : entity.getSkipIndexes()) {
            if (!liveByName.containsKey(entityIdx.getName())) {
                changes.add(new SchemaChange.AddIndex(entityIdx));
            }
        }

        for (LiveIndex liveIdx : live.getIndexes()) {
            if (!entityByName.containsKey(liveIdx.getName())) {
                changes.add(new SchemaChange.DropIndex(liveIdx.getName()));
            }
        }
    }

    private static String resolveEngineName(EntityMetadata<?> entity) {
        String base = toCamelCaseEngine(entity.getEngine().getEngine().name());
        if (entity.getEngine().isReplicated()) {
            return "Replicated" + base;
        }
        return base;
    }

    private static String normaliseEngine(String engine) {
        if (engine == null) {
            return "";
        }
        int paren = engine.indexOf('(');
        return paren >= 0 ? engine.substring(0, paren).strip() : engine.strip();
    }

    private static String normaliseType(String type) {
        if (type == null) {
            return "";
        }
        return type.strip();
    }

    private static String toCamelCaseEngine(String snakeCase) {
        StringBuilder sb = new StringBuilder();
        for (String part : snakeCase.split("_")) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    sb.append(part.substring(1).toLowerCase());
                }
            }
        }
        return sb.toString();
    }
}

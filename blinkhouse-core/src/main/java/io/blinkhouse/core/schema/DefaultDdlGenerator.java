package io.blinkhouse.core.schema;

import io.blinkhouse.core.annotation.ChDictionary;
import io.blinkhouse.core.annotation.Engine;
import io.blinkhouse.core.metadata.ColumnMetadata;
import io.blinkhouse.core.metadata.DictionaryAttributeMetadata;
import io.blinkhouse.core.metadata.DictionaryMetadata;
import io.blinkhouse.core.metadata.EngineMetadata;
import io.blinkhouse.core.metadata.EntityMetadata;
import io.blinkhouse.core.metadata.MaterializedViewMetadata;
import io.blinkhouse.core.metadata.SkipIndexMetadata;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Default {@link DdlGenerator} implementation.
 *
 * <p>Generates ANSI-compatible ClickHouse DDL using backtick-quoted identifiers.
 * Produces complete {@code CREATE TABLE} and {@code ALTER TABLE} statements.
 */
public final class DefaultDdlGenerator implements DdlGenerator {

    @Override
    public String createTable(EntityMetadata<?> metadata, boolean ifNotExists) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE ");
        if (ifNotExists) {
            sb.append("IF NOT EXISTS ");
        }
        sb.append(metadata.getQualifiedName());
        metadata.getOnCluster().ifPresent(c -> sb.append(" ON CLUSTER `").append(c).append('`'));
        sb.append("\n(\n");

        List<? extends ColumnMetadata<?>> cols = metadata.getColumns();
        for (int i = 0; i < cols.size(); i++) {
            ColumnMetadata<?> col = cols.get(i);
            sb.append("    `").append(col.getName()).append("` ").append(col.getChTypeName());
            col.getDefaultExpression().ifPresent(d -> sb.append(" DEFAULT ").append(d));
            if (!col.getCodecs().isEmpty()) {
                sb.append(" CODEC(");
                sb.append(String.join(", ", col.getCodecs()));
                sb.append(")");
            }
            col.getTtl().ifPresent(t -> sb.append(" TTL ").append(t));
            col.getComment().ifPresent(c -> sb.append(" COMMENT '").append(escapeString(c)).append("'"));
            if (i < cols.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }

        for (SkipIndexMetadata idx : metadata.getSkipIndexes()) {
            sb.append("    INDEX `").append(idx.getName()).append("` (").append(idx.getExpression()).append(") ");
            sb.append("TYPE ").append(renderIndexType(idx)).append(" GRANULARITY ").append(idx.getGranularity());
            sb.append(",\n");
        }

        if (sb.charAt(sb.length() - 2) == ',') {
            sb.delete(sb.length() - 2, sb.length() - 1);
        }

        sb.append(")\n");
        sb.append("ENGINE = ").append(renderEngine(metadata.getEngine(), metadata));
        sb.append("\n");

        if (!metadata.getOrderBy().isEmpty()) {
            sb.append("ORDER BY (").append(String.join(", ", metadata.getOrderBy())).append(")\n");
        }
        if (!metadata.getPartitionBy().isEmpty()) {
            sb.append("PARTITION BY (").append(String.join(", ", metadata.getPartitionBy())).append(")\n");
        }
        List<String> pk = metadata.getPrimaryKey();
        boolean pkDiffersFromOrder = !pk.isEmpty() && !pk.equals(metadata.getOrderBy());
        if (pkDiffersFromOrder) {
            sb.append("PRIMARY KEY (").append(String.join(", ", pk)).append(")\n");
        }
        metadata.getTtl().ifPresent(t -> sb.append("TTL ").append(t).append("\n"));

        Map<String, String> settings = metadata.getSettings();
        if (!settings.isEmpty()) {
            sb.append("SETTINGS ");
            List<String> entries = new ArrayList<>();
            for (Map.Entry<String, String> e : settings.entrySet()) {
                entries.add(e.getKey() + " = " + e.getValue());
            }
            sb.append(String.join(", ", entries));
            sb.append("\n");
        }

        return sb.toString().strip();
    }

    @Override
    public List<String> alterStatements(EntityMetadata<?> metadata, List<SchemaChange> changes) {
        List<String> stmts = new ArrayList<>();
        String table = metadata.getQualifiedName();
        for (SchemaChange change : changes) {
            if (change instanceof SchemaChange.AddColumn ac) {
                String stmt = "ALTER TABLE " + table + " ADD COLUMN `" + ac.col() + "` " + ac.chType();
                if (!ac.afterCol().isEmpty()) {
                    stmt += " AFTER `" + ac.afterCol() + "`";
                }
                stmts.add(stmt);
            } else if (change instanceof SchemaChange.DropColumn dc) {
                stmts.add("ALTER TABLE " + table + " DROP COLUMN `" + dc.col() + "`");
            } else if (change instanceof SchemaChange.ModifyColumnType mct) {
                stmts.add("ALTER TABLE " + table + " MODIFY COLUMN `" + mct.col() + "` " + mct.to());
            } else if (change instanceof SchemaChange.AddIndex ai) {
                SkipIndexMetadata idx = ai.idx();
                stmts.add("ALTER TABLE " + table + " ADD INDEX `" + idx.getName() + "` ("
                    + idx.getExpression() + ") TYPE " + renderIndexType(idx)
                    + " GRANULARITY " + idx.getGranularity());
            } else if (change instanceof SchemaChange.DropIndex di) {
                stmts.add("ALTER TABLE " + table + " DROP INDEX `" + di.name() + "`");
            } else if (change instanceof SchemaChange.TtlMismatch tm) {
                if (!tm.expected().isEmpty()) {
                    stmts.add("ALTER TABLE " + table + " MODIFY TTL " + tm.expected());
                }
            } else if (change instanceof SchemaChange.EngineMismatch) {
                // never auto-fixable
            } else if (change instanceof SchemaChange.OrderByMismatch) {
                // never auto-fixable
            }
        }
        return stmts;
    }

    @Override
    public String createMaterializedView(MaterializedViewMetadata mv, boolean ifNotExists) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE MATERIALIZED VIEW ");
        if (ifNotExists) {
            sb.append("IF NOT EXISTS ");
        }
        sb.append(mv.getQualifiedName());
        mv.getOnCluster().ifPresent(c -> sb.append(" ON CLUSTER `").append(c).append('`'));

        if (!mv.getTargetTable().isEmpty()) {
            sb.append(" TO ").append(mv.getTargetTable());
        }

        if (mv.isPopulate()) {
            sb.append(" POPULATE");
        }

        sb.append(" AS\n").append(mv.getSelectSql());
        return sb.toString().strip();
    }

    @Override
    public String createDictionary(DictionaryMetadata dict, boolean ifNotExists) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE DICTIONARY ");
        if (ifNotExists) {
            sb.append("IF NOT EXISTS ");
        }
        sb.append(dict.getQualifiedName());
        dict.getOnCluster().ifPresent(c -> sb.append(" ON CLUSTER `").append(c).append('`'));
        sb.append("\n(\n");

        List<DictionaryAttributeMetadata> keys = new ArrayList<>();
        List<DictionaryAttributeMetadata> attrs = new ArrayList<>();
        for (DictionaryAttributeMetadata a : dict.getAttributes()) {
            if (a.isKey()) {
                keys.add(a);
            } else {
                attrs.add(a);
            }
        }

        // Key section
        boolean complexKey = keys.size() > 1
                || dict.getLayout() == ChDictionary.Layout.COMPLEX_KEY_HASHED
                || dict.getLayout() == ChDictionary.Layout.COMPLEX_KEY_CACHE;
        if (complexKey) {
            for (DictionaryAttributeMetadata k : keys) {
                sb.append("    `").append(k.getName()).append("` ").append(k.getChTypeName()).append(",\n");
            }
        } else if (!keys.isEmpty()) {
            DictionaryAttributeMetadata k = keys.get(0);
            sb.append("    `").append(k.getName()).append("` ").append(k.getChTypeName()).append(",\n");
        }

        for (int i = 0; i < attrs.size(); i++) {
            DictionaryAttributeMetadata a = attrs.get(i);
            sb.append("    `").append(a.getName()).append("` ").append(a.getChTypeName());
            if (!a.getNullValue().isEmpty()) {
                sb.append(" DEFAULT '").append(escapeString(a.getNullValue())).append("'");
            }
            if (i < attrs.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append(")\n");

        // PRIMARY KEY
        if (complexKey) {
            sb.append("PRIMARY KEY ");
            List<String> keyNames = new ArrayList<>();
            for (DictionaryAttributeMetadata k : keys) {
                keyNames.add("`" + k.getName() + "`");
            }
            sb.append(String.join(", ", keyNames)).append("\n");
        } else if (!keys.isEmpty()) {
            sb.append("PRIMARY KEY `").append(keys.get(0).getName()).append("`\n");
        }

        // SOURCE
        sb.append("SOURCE(").append(renderDictSource(dict)).append(")\n");

        // LAYOUT
        sb.append("LAYOUT(").append(dict.getLayout().name()).append("())\n");

        // LIFETIME
        if (dict.getLifetimeMin() == dict.getLifetimeMax()) {
            sb.append("LIFETIME(").append(dict.getLifetimeMin()).append(")");
        } else {
            sb.append("LIFETIME(MIN ").append(dict.getLifetimeMin())
              .append(" MAX ").append(dict.getLifetimeMax()).append(")");
        }

        return sb.toString().strip();
    }

    private String renderDictSource(DictionaryMetadata dict) {
        switch (dict.getSourceType()) {
            case CLICKHOUSE: {
                StringBuilder s = new StringBuilder("CLICKHOUSE(TABLE '");
                s.append(escapeString(dict.getSourceTable())).append("'");
                if (!dict.getSourceWhere().isEmpty()) {
                    s.append(" WHERE '").append(escapeString(dict.getSourceWhere())).append("'");
                }
                s.append(")");
                return s.toString();
            }
            case MYSQL:
                return "MYSQL(TABLE '" + escapeString(dict.getSourceTable()) + "')";
            case POSTGRESQL:
                return "POSTGRESQL(TABLE '" + escapeString(dict.getSourceTable()) + "')";
            case HTTP:
                return "HTTP(URL '" + escapeString(dict.getSourceTable()) + "' FORMAT TSV)";
            case FILE:
                return "FILE(PATH '" + escapeString(dict.getSourceTable()) + "' FORMAT TSV)";
            default:
                return "CLICKHOUSE(TABLE '" + escapeString(dict.getSourceTable()) + "')";
        }
    }

    private String renderEngine(EngineMetadata em, EntityMetadata<?> entity) {
        String base = toCamelCase(em.getEngine().name());
        if (em.isReplicated()) {
            base = "Replicated" + base;
        }

        if (em.getEngine() == Engine.REPLACING_MERGE_TREE) {
            String ver = em.getVersionColumn().orElse("");
            if (!ver.isEmpty()) {
                return base + "(" + ver + ")";
            }
            return base + "()";
        }
        if (em.getEngine() == Engine.SUMMING_MERGE_TREE) {
            List<String> sums = em.getSummingColumns();
            if (!sums.isEmpty()) {
                return base + "(" + String.join(", ", sums) + ")";
            }
            return base + "()";
        }
        if (em.getEngine() == Engine.COLLAPSING_MERGE_TREE
                || em.getEngine() == Engine.VERSIONED_COLLAPSING_MERGE_TREE) {
            String sign = em.getSignColumn().orElse("");
            String ver = em.getVersionCollapsingColumn().orElse("");
            if (!sign.isEmpty() && !ver.isEmpty()) {
                return base + "(" + sign + ", " + ver + ")";
            }
            if (!sign.isEmpty()) {
                return base + "(" + sign + ")";
            }
            return base + "()";
        }
        if (em.getEngine() == Engine.DISTRIBUTED) {
            String cluster = em.getCluster().orElse("");
            String db = entity.getDatabase();
            String local = em.getLocalTable().orElse("");
            String shardKey = em.getShardingKey().orElse("rand()");
            return base + "(" + cluster + ", " + db + ", " + local + ", " + shardKey + ")";
        }
        if (em.isReplicated()) {
            return base + "('" + em.getZkPath() + "', '" + em.getReplica() + "')";
        }
        return base + "()";
    }

    private String renderIndexType(SkipIndexMetadata idx) {
        String typeName = idx.getType().name().toLowerCase();
        List<String> params = idx.getParams();
        if (!params.isEmpty()) {
            return typeName + "(" + String.join(", ", params) + ")";
        }
        return typeName;
    }

    private static String toCamelCase(String snakeCase) {
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

    private static String escapeString(String s) {
        return s.replace("'", "\\'");
    }
}

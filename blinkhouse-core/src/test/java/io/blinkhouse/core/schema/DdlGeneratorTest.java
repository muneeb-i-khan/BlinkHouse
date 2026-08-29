package io.blinkhouse.core.schema;

import io.blinkhouse.core.annotation.ChCodec;
import io.blinkhouse.core.annotation.ChColumn;
import io.blinkhouse.core.annotation.ChEngine;
import io.blinkhouse.core.annotation.ChSkipIndex;
import io.blinkhouse.core.annotation.ChTable;
import io.blinkhouse.core.annotation.Engine;
import io.blinkhouse.core.annotation.IndexType;
import io.blinkhouse.core.metadata.EntityMetadata;
import io.blinkhouse.core.metadata.EntityMetadataFactory;
import io.blinkhouse.core.type.TypeRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DefaultDdlGenerator}.
 */
class DdlGeneratorTest {

    @ChTable(
        name = "page_views",
        orderBy = {"tenant_id", "ts"},
        partitionBy = "toYYYYMM(ts)",
        ttl = "ts + INTERVAL 90 DAY DELETE"
    )
    @ChEngine(value = Engine.REPLACING_MERGE_TREE, versionColumn = "ingested_at")
    @ChSkipIndex(name = "idx_url", expression = "url", type = IndexType.TOKENBF_V1, granularity = 4)
    record PageView(
        @ChColumn(type = "UInt32") int tenantId,
        @ChColumn(type = "DateTime64(3,'UTC')") Instant ts,
        UUID userId,
        @ChColumn(type = "LowCardinality(String)") String country,
        @ChCodec({"ZSTD(3)"}) String url,
        @ChColumn(defaultExpression = "now64(3)", type = "DateTime64(9,'UTC')") Instant ingestedAt
    ) {}

    @ChTable(name = "events", orderBy = "id")
    record SimpleEvent(long id, String name) {}

    private DdlGenerator generator;
    private EntityMetadataFactory factory;

    @BeforeEach
    void setUp() {
        factory = new EntityMetadataFactory(TypeRegistry.withDefaults());
        generator = new DefaultDdlGenerator();
    }

    @Test
    void createTable_containsTableName() {
        EntityMetadata<SimpleEvent> md = factory.resolve(SimpleEvent.class);
        String ddl = generator.createTable(md, false);
        assertThat(ddl).contains("`events`");
    }

    @Test
    void createTable_containsIfNotExists() {
        EntityMetadata<SimpleEvent> md = factory.resolve(SimpleEvent.class);
        String ddl = generator.createTable(md, true);
        assertThat(ddl).contains("IF NOT EXISTS");
    }

    @Test
    void createTable_containsEngineReplacingMergeTree() {
        EntityMetadata<PageView> md = factory.resolve(PageView.class);
        String ddl = generator.createTable(md, false);
        assertThat(ddl).contains("ReplacingMergeTree(ingested_at)");
    }

    @Test
    void createTable_containsOrderBy() {
        EntityMetadata<PageView> md = factory.resolve(PageView.class);
        String ddl = generator.createTable(md, false);
        assertThat(ddl).contains("ORDER BY");
        assertThat(ddl).contains("tenant_id");
    }

    @Test
    void createTable_containsPartitionBy() {
        EntityMetadata<PageView> md = factory.resolve(PageView.class);
        String ddl = generator.createTable(md, false);
        assertThat(ddl).contains("PARTITION BY");
        assertThat(ddl).contains("toYYYYMM(ts)");
    }

    @Test
    void createTable_containsTtl() {
        EntityMetadata<PageView> md = factory.resolve(PageView.class);
        String ddl = generator.createTable(md, false);
        assertThat(ddl).contains("TTL ts + INTERVAL 90 DAY DELETE");
    }

    @Test
    void createTable_containsSkipIndex() {
        EntityMetadata<PageView> md = factory.resolve(PageView.class);
        String ddl = generator.createTable(md, false);
        assertThat(ddl).contains("INDEX `idx_url`");
        assertThat(ddl).contains("tokenbf_v1");
        assertThat(ddl).contains("GRANULARITY 4");
    }

    @Test
    void createTable_containsCodec() {
        EntityMetadata<PageView> md = factory.resolve(PageView.class);
        String ddl = generator.createTable(md, false);
        assertThat(ddl).contains("CODEC(ZSTD(3))");
    }

    @Test
    void createTable_containsDefaultExpression() {
        EntityMetadata<PageView> md = factory.resolve(PageView.class);
        String ddl = generator.createTable(md, false);
        assertThat(ddl).contains("DEFAULT now64(3)");
    }

    @Test
    void alterStatements_addColumn_generatesAlter() {
        EntityMetadata<SimpleEvent> md = factory.resolve(SimpleEvent.class);
        SchemaChange.AddColumn change = new SchemaChange.AddColumn("new_col", "String", "id");
        java.util.List<String> stmts = generator.alterStatements(md, java.util.List.of(change));
        assertThat(stmts).hasSize(1);
        assertThat(stmts.get(0)).contains("ADD COLUMN `new_col` String");
        assertThat(stmts.get(0)).contains("AFTER `id`");
    }

    @Test
    void alterStatements_dropColumn_generatesAlter() {
        EntityMetadata<SimpleEvent> md = factory.resolve(SimpleEvent.class);
        SchemaChange.DropColumn change = new SchemaChange.DropColumn("old_col");
        java.util.List<String> stmts = generator.alterStatements(md, java.util.List.of(change));
        assertThat(stmts).hasSize(1);
        assertThat(stmts.get(0)).contains("DROP COLUMN `old_col`");
    }

    @Test
    void alterStatements_engineMismatch_producesNoStatement() {
        EntityMetadata<SimpleEvent> md = factory.resolve(SimpleEvent.class);
        SchemaChange.EngineMismatch change = new SchemaChange.EngineMismatch("MergeTree", "Log");
        java.util.List<String> stmts = generator.alterStatements(md, java.util.List.of(change));
        assertThat(stmts).isEmpty();
    }
}

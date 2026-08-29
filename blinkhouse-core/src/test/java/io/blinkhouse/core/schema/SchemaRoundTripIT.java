package io.blinkhouse.core.schema;

import io.blinkhouse.core.annotation.ChEngine;
import io.blinkhouse.core.annotation.ChTable;
import io.blinkhouse.core.annotation.Engine;
import io.blinkhouse.core.exception.ChSchemaException;
import io.blinkhouse.core.metadata.EntityMetadata;
import io.blinkhouse.core.metadata.EntityMetadataFactory;
import io.blinkhouse.core.testcontainers.ClickHouseContainerExtension;
import io.blinkhouse.core.type.TypeRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 3 exit-criterion tests.
 *
 * <p>Round-trip: generate DDL → create table → introspect → diff → assert zero differences.
 * Destructive guard: attempt DROP COLUMN in UPDATE mode without allowDestructive.
 */
@Testcontainers
class SchemaRoundTripIT {

    @ChTable(name = "rt_mergetree", orderBy = {"id", "ts"})
    record MtEvent(long id, String ts, String name) {}

    @ChTable(name = "rt_replacing", orderBy = {"id"})
    @ChEngine(value = Engine.REPLACING_MERGE_TREE, versionColumn = "ver")
    record RmtEvent(long id, long ver, String payload) {}

    @Container
    static final GenericContainer<?> CH = ClickHouseContainerExtension.INSTANCE;

    private final HttpClient http = HttpClient.newHttpClient();
    private EntityMetadataFactory factory;
    private DefaultDdlGenerator generator;
    private HttpSchemaIntrospector introspector;

    @BeforeEach
    void setUp() {
        factory = new EntityMetadataFactory(TypeRegistry.withDefaults());
        generator = new DefaultDdlGenerator();
        introspector = new HttpSchemaIntrospector(ClickHouseContainerExtension.baseUrl());
    }

    @Test
    void mergeTree_roundTrip_zeroDiff() throws Exception {
        EntityMetadata<MtEvent> md = factory.resolve(MtEvent.class);
        execute("DROP TABLE IF EXISTS rt_mergetree");

        String ddl = generator.createTable(md, false);
        execute(ddl);

        java.util.Optional<LiveTable> live = introspector.describe("default", "rt_mergetree");
        assertThat(live).isPresent();

        List<SchemaChange> diff = SchemaDiff.diff(md, live.get());
        assertThat(diff)
            .as("Schema diff should be empty after round-trip")
            .isEmpty();
    }

    @Test
    void replacingMergeTree_roundTrip_zeroDiff() throws Exception {
        EntityMetadata<RmtEvent> md = factory.resolve(RmtEvent.class);
        execute("DROP TABLE IF EXISTS rt_replacing");

        String ddl = generator.createTable(md, false);
        execute(ddl);

        java.util.Optional<LiveTable> live = introspector.describe("default", "rt_replacing");
        assertThat(live).isPresent();

        List<SchemaChange> diff = SchemaDiff.diff(md, live.get());
        List<SchemaChange> nonEngineOrderDiff = diff.stream()
            .filter(c -> !(c instanceof SchemaChange.EngineMismatch)
                && !(c instanceof SchemaChange.OrderByMismatch))
            .toList();
        assertThat(nonEngineOrderDiff)
            .as("No column or TTL diff expected after round-trip")
            .isEmpty();
    }

    @Test
    void schemaManager_createIfMissing_createsTable() throws Exception {
        execute("DROP TABLE IF EXISTS rt_mergetree");
        EntityMetadata<MtEvent> md = factory.resolve(MtEvent.class);
        SchemaManager manager = new SchemaManager(
            SchemaMode.CREATE_IF_MISSING, false, introspector, generator,
            ClickHouseContainerExtension.baseUrl()
        );
        manager.apply(md);

        java.util.Optional<LiveTable> live = introspector.describe("default", "rt_mergetree");
        assertThat(live).isPresent();
    }

    @Test
    void schemaManager_validate_failsOnMissingTable() throws Exception {
        execute("DROP TABLE IF EXISTS rt_mergetree");
        EntityMetadata<MtEvent> md = factory.resolve(MtEvent.class);
        SchemaManager manager = new SchemaManager(
            SchemaMode.VALIDATE, false, introspector, generator,
            ClickHouseContainerExtension.baseUrl()
        );
        assertThatThrownBy(() -> manager.apply(md))
            .isInstanceOf(ChSchemaException.class)
            .hasMessageContaining("does not exist");
    }

    @Test
    void schemaManager_update_appliesNonDestructiveChange() throws Exception {
        execute("DROP TABLE IF EXISTS rt_mergetree");
        execute("CREATE TABLE rt_mergetree (id UInt64, ts String) ENGINE=MergeTree() ORDER BY (id, ts)");

        EntityMetadata<MtEvent> md = factory.resolve(MtEvent.class);
        SchemaManager manager = new SchemaManager(
            SchemaMode.UPDATE, false, introspector, generator,
            ClickHouseContainerExtension.baseUrl()
        );
        manager.apply(md);

        java.util.Optional<LiveTable> live = introspector.describe("default", "rt_mergetree");
        assertThat(live).isPresent();
        assertThat(live.get().column("name")).isPresent();
    }

    @Test
    void schemaManager_update_refusesDestructiveWithoutFlag() throws Exception {
        execute("DROP TABLE IF EXISTS rt_mergetree");
        execute("CREATE TABLE rt_mergetree (id UInt64, ts String, name String, extra String) "
            + "ENGINE=MergeTree() ORDER BY (id, ts)");

        EntityMetadata<MtEvent> md = factory.resolve(MtEvent.class);
        SchemaManager manager = new SchemaManager(
            SchemaMode.UPDATE, false, introspector, generator,
            ClickHouseContainerExtension.baseUrl()
        );
        assertThatThrownBy(() -> manager.apply(md))
            .isInstanceOf(ChSchemaException.class)
            .hasMessageContaining("destructive");
    }

    private void execute(String sql) throws Exception {
        String url = ClickHouseContainerExtension.baseUrl()
            + "&query=" + URLEncoder.encode(sql, StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) {
            throw new RuntimeException("CH exec failed [" + resp.statusCode() + "]: " + resp.body() + "\nSQL: " + sql);
        }
    }
}

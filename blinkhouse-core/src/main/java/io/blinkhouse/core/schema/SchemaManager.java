package io.blinkhouse.core.schema;

import io.blinkhouse.core.exception.ChException;
import io.blinkhouse.core.exception.ChExceptionTranslator;
import io.blinkhouse.core.exception.ChSchemaException;
import io.blinkhouse.core.metadata.EntityMetadata;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies the {@link SchemaMode} state machine at startup.
 *
 * <p>Decision table (ADR-10):
 * <table border="1">
 * <tr><th>Mode</th><th>Table missing</th><th>Non-destructive drift</th>
 *     <th>Destructive drift</th><th>Engine/OrderBy mismatch</th></tr>
 * <tr><td>NONE</td><td>ignore</td><td>ignore</td><td>ignore</td><td>ignore</td></tr>
 * <tr><td>VALIDATE</td><td>fail</td><td>fail</td><td>fail</td><td>fail</td></tr>
 * <tr><td>CREATE_IF_MISSING</td><td>create</td><td>WARN</td><td>WARN</td><td>WARN</td></tr>
 * <tr><td>UPDATE</td><td>create</td><td>ALTER</td><td>fail unless allowDestructive</td>
 *     <td>fail always</td></tr>
 * </table>
 *
 * <p>VALIDATE failures print a human-readable table, not a stack trace.
 */
public final class SchemaManager {

    private static final Logger LOG = LoggerFactory.getLogger(SchemaManager.class);

    private final SchemaMode mode;
    private final boolean allowDestructive;
    private final SchemaIntrospector introspector;
    private final DdlGenerator ddlGenerator;
    private final String baseUrl;
    private final HttpClient http;

    /**
     * Constructs a schema manager.
     *
     * @param mode            the schema mode
     * @param allowDestructive whether destructive ALTER TABLE statements are permitted in UPDATE mode
     * @param introspector    the schema introspector
     * @param ddlGenerator    the DDL generator
     * @param baseUrl         the ClickHouse HTTP base URL with credentials
     */
    public SchemaManager(
            SchemaMode mode,
            boolean allowDestructive,
            SchemaIntrospector introspector,
            DdlGenerator ddlGenerator,
            String baseUrl) {
        this.mode = mode;
        this.allowDestructive = allowDestructive;
        this.introspector = introspector;
        this.ddlGenerator = ddlGenerator;
        this.baseUrl = baseUrl;
        this.http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    /**
     * Applies the schema mode state machine for the given entity.
     *
     * <p>This method is idempotent — calling it multiple times with the same entity
     * in NONE or VALIDATE mode has no side effects.
     *
     * @param <T>      the entity type
     * @param metadata the resolved entity metadata
     * @throws ChSchemaException if validation fails or a required DDL operation fails
     */
    public <T> void apply(EntityMetadata<T> metadata) throws ChSchemaException {
        if (mode == SchemaMode.NONE) {
            return;
        }

        String database = metadata.getDatabase().isEmpty() ? "default" : metadata.getDatabase();
        String table = metadata.getTable();
        Optional<LiveTable> live = introspector.describe(database, table);

        if (live.isEmpty()) {
            handleMissingTable(metadata, database, table);
            return;
        }

        List<SchemaChange> changes = SchemaDiff.diff(metadata, live.get());
        if (changes.isEmpty()) {
            return;
        }

        switch (mode) {
            case VALIDATE -> throwValidationFailure(metadata.getQualifiedName(), changes);
            case CREATE_IF_MISSING -> warnDrift(metadata.getQualifiedName(), changes);
            case UPDATE -> applyUpdate(metadata, changes);
            default -> throw new IllegalStateException("Unknown SchemaMode: " + mode);
        }
    }

    private <T> void handleMissingTable(EntityMetadata<T> metadata, String database, String table) {
        if (mode == SchemaMode.VALIDATE) {
            throw new ChSchemaException(
                "ClickORM schema validation failed: table "
                + metadata.getQualifiedName() + " does not exist");
        }
        if (mode == SchemaMode.CREATE_IF_MISSING || mode == SchemaMode.UPDATE) {
            LOG.info("Creating table {}", metadata.getQualifiedName());
            String ddl = ddlGenerator.createTable(metadata, true);
            execute(ddl);
        }
    }

    private void throwValidationFailure(String qualifiedName, List<SchemaChange> changes) {
        StringBuilder msg = new StringBuilder();
        msg.append("ClickORM schema validation failed for ").append(qualifiedName).append("\n");
        for (SchemaChange change : changes) {
            if (change instanceof SchemaChange.AddColumn ac) {
                msg.append("  ✗ column `").append(ac.col()).append("` MISSING on server\n");
            } else if (change instanceof SchemaChange.DropColumn dc) {
                msg.append("  ✗ column `").append(dc.col()).append("` exists on server but not in entity\n");
            } else if (change instanceof SchemaChange.ModifyColumnType mct) {
                msg.append("  ✗ column `").append(mct.col()).append("`")
                    .append("  expected ").append(mct.to()).append("  actual ").append(mct.from()).append("\n");
            } else if (change instanceof SchemaChange.EngineMismatch em) {
                msg.append("  ✗ ENGINE  expected ").append(em.expected())
                    .append("  actual ").append(em.actual()).append("\n");
            } else if (change instanceof SchemaChange.OrderByMismatch ob) {
                msg.append("  ✗ ORDER BY  expected (").append(String.join(", ", ob.expected()))
                    .append(")  actual (").append(String.join(", ", ob.actual())).append(")\n");
                msg.append("    → ORDER BY changes require a table rebuild; ClickORM will not attempt this.\n");
            } else if (change instanceof SchemaChange.TtlMismatch tm) {
                msg.append("  ✗ TTL  expected ").append(tm.expected())
                    .append("  actual ").append(tm.actual()).append("\n");
            } else if (change instanceof SchemaChange.AddIndex ai) {
                msg.append("  ✗ index `").append(ai.idx().getName()).append("` MISSING on server\n");
            } else if (change instanceof SchemaChange.DropIndex di) {
                msg.append("  ✗ index `").append(di.name()).append("` exists on server but not in entity\n");
            }
        }
        throw new ChSchemaException(msg.toString());
    }

    private void warnDrift(String qualifiedName, List<SchemaChange> changes) {
        for (SchemaChange change : changes) {
            LOG.warn("Schema drift detected for {}: {}", qualifiedName, change);
        }
    }

    private <T> void applyUpdate(EntityMetadata<T> metadata, List<SchemaChange> changes) {
        List<SchemaChange> unfixable = new ArrayList<>();
        List<SchemaChange> destructive = new ArrayList<>();
        List<SchemaChange> safe = new ArrayList<>();

        for (SchemaChange change : changes) {
            if (change instanceof SchemaChange.EngineMismatch || change instanceof SchemaChange.OrderByMismatch) {
                unfixable.add(change);
            } else if (change.destructive()) {
                destructive.add(change);
            } else {
                safe.add(change);
            }
        }

        if (!unfixable.isEmpty()) {
            throwValidationFailure(metadata.getQualifiedName(), unfixable);
        }

        if (!destructive.isEmpty() && !allowDestructive) {
            StringBuilder msg = new StringBuilder();
            msg.append("ClickORM: destructive schema changes detected for ")
                .append(metadata.getQualifiedName())
                .append(" but allowDestructive is false.\n")
                .append("Set allowDestructive=true to apply these changes:\n");
            for (SchemaChange c : destructive) {
                msg.append("  - ").append(c).append("\n");
            }
            throw new ChSchemaException(msg.toString());
        }

        List<SchemaChange> toApply = new ArrayList<>(safe);
        if (allowDestructive) {
            toApply.addAll(destructive);
        }

        List<String> stmts = ddlGenerator.alterStatements(metadata, toApply);
        for (String stmt : stmts) {
            LOG.info("Applying schema change: {}", stmt);
            execute(stmt);
        }
    }

    private void execute(String sql) {
        String url = baseUrl + "&query=" + java.net.URLEncoder.encode(sql, StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();
        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                ChException ex = ChExceptionTranslator.translate(resp.body(), resp.statusCode());
                throw new ChSchemaException("DDL execution failed: " + resp.body(), ex);
            }
        } catch (IOException e) {
            throw new ChSchemaException("DDL execution failed (network): " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ChSchemaException("DDL execution interrupted", e);
        }
    }
}

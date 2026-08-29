package io.blinkhouse.core.schema;

import io.blinkhouse.core.metadata.EntityMetadata;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Emits detected schema changes as timestamped {@code .sql} migration scripts.
 *
 * <p>The generated scripts are compatible with Flyway and Liquibase versioned migrations.
 * Script names follow the pattern {@code Vyyyymmddhhmmss__<table>_migration.sql}.
 *
 * <p>This class does <em>not</em> execute any DDL — it only writes files. Use it as an
 * alternative to {@link SchemaManager} when schema changes require DBA review before
 * application.
 */
public final class MigrationScriptWriter {

    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final DdlGenerator ddlGenerator;
    private final Path outputDirectory;

    /**
     * Constructs a migration script writer.
     *
     * @param ddlGenerator    the generator used to produce ALTER TABLE statements
     * @param outputDirectory the directory in which to write migration scripts
     */
    public MigrationScriptWriter(DdlGenerator ddlGenerator, Path outputDirectory) {
        this.ddlGenerator = ddlGenerator;
        this.outputDirectory = outputDirectory;
    }

    /**
     * Writes a migration script for the given changes.
     *
     * <p>If the diff is empty, no file is written and {@code null} is returned.
     *
     * @param metadata the entity metadata (used for the qualified table name and ALTER statements)
     * @param changes  the schema changes to emit
     * @return the path of the written file, or {@code null} if no changes
     * @throws IOException if writing fails
     */
    public Path write(EntityMetadata<?> metadata, List<SchemaChange> changes) throws IOException {
        if (changes.isEmpty()) {
            return null;
        }

        List<String> stmts = ddlGenerator.alterStatements(metadata, changes);
        if (stmts.isEmpty()) {
            return null;
        }

        String timestamp = LocalDateTime.now().format(TS_FORMAT);
        String tableName = metadata.getTable().replaceAll("[^a-zA-Z0-9_]", "_");
        String filename = "V" + timestamp + "__" + tableName + "_migration.sql";

        Files.createDirectories(outputDirectory);
        Path output = outputDirectory.resolve(filename);
        try (Writer w = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            w.write("-- BlinkHouse auto-generated migration for " + metadata.getQualifiedName() + "\n");
            w.write("-- Generated at " + LocalDateTime.now() + "\n");
            w.write("-- Review before applying to production.\n\n");
            for (String stmt : stmts) {
                w.write(stmt);
                w.write(";\n\n");
            }
        }
        return output;
    }

    /**
     * Writes a {@code CREATE TABLE} migration script for a new table.
     *
     * @param metadata    the entity metadata
     * @param ifNotExists whether to emit {@code IF NOT EXISTS}
     * @return the path of the written file
     * @throws IOException if writing fails
     */
    public Path writeCreate(EntityMetadata<?> metadata, boolean ifNotExists) throws IOException {
        String ddl = ddlGenerator.createTable(metadata, ifNotExists);
        String timestamp = LocalDateTime.now().format(TS_FORMAT);
        String tableName = metadata.getTable().replaceAll("[^a-zA-Z0-9_]", "_");
        String filename = "V" + timestamp + "__create_" + tableName + ".sql";

        Files.createDirectories(outputDirectory);
        Path output = outputDirectory.resolve(filename);
        try (Writer w = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            w.write("-- BlinkHouse auto-generated CREATE TABLE for " + metadata.getQualifiedName() + "\n");
            w.write("-- Generated at " + LocalDateTime.now() + "\n\n");
            w.write(ddl);
            w.write(";\n");
        }
        return output;
    }
}

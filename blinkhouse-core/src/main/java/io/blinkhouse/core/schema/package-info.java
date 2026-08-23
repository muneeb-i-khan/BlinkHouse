/**
 * Schema management: DDL generation, introspection, drift detection, and migration.
 *
 * <p><strong>Safety rule (ADR-10):</strong> {@code UPDATE} mode requires two independent
 * opt-ins for destructive changes ({@code mode: UPDATE} AND {@code allowDestructive: true}).
 * {@code EngineMismatch} and {@code OrderByMismatch} are never auto-fixable — they require
 * a table rebuild and are always reported as errors.
 *
 * <ul>
 *   <li>{@code DdlGenerator} — entity metadata → CREATE TABLE DDL string</li>
 *   <li>{@code SchemaIntrospector} — reads {@code system.tables} / {@code system.columns} into a {@code LiveTable} model</li>
 *   <li>{@code SchemaDiff} — compares {@code EntityMetadata} against {@code LiveTable}, produces a typed change list</li>
 *   <li>{@code SchemaChange} — sealed interface: AddColumn, DropColumn, ModifyColumnType, AddIndex, …</li>
 *   <li>{@code SchemaManager} — NONE → VALIDATE → CREATE_IF_MISSING → UPDATE state machine</li>
 *   <li>{@code SchemaMode} — enum for the four modes</li>
 *   <li>{@code MigrationScriptWriter} — emits timestamped .sql files for Flyway/Liquibase</li>
 * </ul>
 */
package io.blinkhouse.core.schema;

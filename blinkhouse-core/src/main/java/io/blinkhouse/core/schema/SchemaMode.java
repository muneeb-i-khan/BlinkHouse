package io.blinkhouse.core.schema;

/**
 * Controls how {@link SchemaManager} handles schema drift at startup.
 *
 * <p>The modes form a strictness ladder:
 * <pre>
 *   NONE  →  VALIDATE  →  CREATE_IF_MISSING  →  UPDATE
 * </pre>
 *
 * <p>In UPDATE mode, destructive changes (DROP COLUMN, DROP INDEX, type narrowing) require
 * a separate {@code allowDestructive=true} opt-in. ENGINE and ORDER BY mismatches are never
 * auto-applied regardless of mode (they require a full table rebuild).
 */
public enum SchemaMode {

    /**
     * Ignore all schema drift. No checks, no DDL. Default mode.
     * Use in production when schema changes are managed externally (Flyway, Liquibase).
     */
    NONE,

    /**
     * Check for drift at startup and fail fast if any is detected.
     * Prints a human-readable diff table, not a stack trace.
     * Does not modify the database.
     */
    VALIDATE,

    /**
     * Create the table if it does not exist. If the table exists, log warnings for
     * any detected drift but do not apply changes.
     */
    CREATE_IF_MISSING,

    /**
     * Create the table if missing; apply non-destructive {@code ALTER TABLE} statements
     * for detected drift. Destructive changes require {@code allowDestructive=true}.
     * ENGINE and ORDER BY mismatches always fail.
     */
    UPDATE
}

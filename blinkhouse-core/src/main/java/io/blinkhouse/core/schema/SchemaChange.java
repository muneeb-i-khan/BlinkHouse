package io.blinkhouse.core.schema;

import io.blinkhouse.core.metadata.SkipIndexMetadata;
import java.util.List;

/**
 * A single detected difference between the entity definition and the live table schema.
 *
 * <p>Each record is produced by {@link SchemaDiff} and consumed by {@link SchemaManager}
 * to decide whether and how to apply the change.
 *
 * <p>{@link #destructive()} returns {@code true} for changes that may cause data loss.
 * {@link EngineMismatch} and {@link OrderByMismatch} are never auto-fixable — they require
 * a full table rebuild and {@link SchemaManager} will always refuse them.
 */
public sealed interface SchemaChange {

    /**
     * Returns {@code true} if applying this change may cause data loss.
     *
     * <p>{@link DropColumn} and {@link DropIndex} are always destructive.
     * {@link ModifyColumnType} is destructive unless the change is a documented
     * safe widening (e.g. UInt32 → UInt64).
     */
    boolean destructive();

    /**
     * A new column must be added to the live table.
     *
     * @param col      the column name
     * @param chType   the ClickHouse type string
     * @param afterCol the column after which to insert; empty means append
     */
    record AddColumn(String col, String chType, String afterCol) implements SchemaChange {

        @Override
        public boolean destructive() {
            return false;
        }
    }

    /**
     * A column exists on the live table but is absent from the entity definition.
     * Applying this change removes the column and all its data.
     *
     * @param col the column name to drop
     */
    record DropColumn(String col) implements SchemaChange {

        @Override
        public boolean destructive() {
            return true;
        }
    }

    /**
     * A column's type on the live table differs from the entity definition.
     *
     * @param col  the column name
     * @param from the live table type
     * @param to   the expected type from the entity definition
     */
    record ModifyColumnType(String col, String from, String to) implements SchemaChange {

        private static final java.util.Set<String> SAFE_WIDINGS = java.util.Set.of(
            "UInt8->UInt16", "UInt8->UInt32", "UInt8->UInt64",
            "UInt16->UInt32", "UInt16->UInt64",
            "UInt32->UInt64",
            "Int8->Int16", "Int8->Int32", "Int8->Int64",
            "Int16->Int32", "Int16->Int64",
            "Int32->Int64"
        );

        @Override
        public boolean destructive() {
            return !SAFE_WIDINGS.contains(from + "->" + to);
        }
    }

    /**
     * A data-skipping index declared in the entity is absent from the live table.
     *
     * @param idx the index metadata to add
     */
    record AddIndex(SkipIndexMetadata idx) implements SchemaChange {

        @Override
        public boolean destructive() {
            return false;
        }
    }

    /**
     * A data-skipping index exists on the live table but is absent from the entity definition.
     *
     * @param name the index name to drop
     */
    record DropIndex(String name) implements SchemaChange {

        @Override
        public boolean destructive() {
            return true;
        }
    }

    /**
     * The engine on the live table does not match the entity's {@code @ChEngine} declaration.
     *
     * <p>Engine mismatches are <strong>never</strong> auto-fixable. They require a full
     * table rebuild (CREATE new → INSERT SELECT → DROP old → RENAME).
     *
     * @param expected the engine declared in {@code @ChEngine}
     * @param actual   the engine on the live table
     */
    record EngineMismatch(String expected, String actual) implements SchemaChange {

        @Override
        public boolean destructive() {
            return true;
        }
    }

    /**
     * The {@code ORDER BY} keys on the live table differ from the entity definition.
     *
     * <p>ORDER BY mismatches are <strong>never</strong> auto-fixable without a table rebuild.
     *
     * @param expected ORDER BY columns from the entity definition
     * @param actual   ORDER BY columns from the live table
     */
    record OrderByMismatch(List<String> expected, List<String> actual) implements SchemaChange {

        @Override
        public boolean destructive() {
            return true;
        }
    }

    /**
     * The {@code TTL} expression on the live table differs from the entity definition.
     *
     * @param expected the TTL expression declared in {@code @ChTable}
     * @param actual   the TTL expression on the live table
     */
    record TtlMismatch(String expected, String actual) implements SchemaChange {

        @Override
        public boolean destructive() {
            return false;
        }
    }
}

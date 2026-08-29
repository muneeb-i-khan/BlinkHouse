package io.blinkhouse.core.query.ast;

import java.util.regex.Pattern;

/**
 * A reference to a ClickHouse table, optionally qualified with a database.
 *
 * <p>Both database and table names are validated against the safe-identifier pattern
 * before use to prevent SQL injection (NFR-6).
 */
public record TableRef(String database, String table) {

    private static final Pattern SAFE_IDENT = Pattern.compile("^[A-Za-z_][0-9A-Za-z_]*$");

    /**
     * Constructs a table reference, validating both names.
     *
     * @param database the database qualifier (may be {@code null} for the current database)
     * @param table    the table name (must not be blank)
     */
    public TableRef {
        if (table == null || table.isBlank()) {
            throw new IllegalArgumentException("TableRef table name must not be blank");
        }
        if (!SAFE_IDENT.matcher(table).matches()) {
            throw new IllegalArgumentException("TableRef table name contains unsafe characters: " + table);
        }
        if (database != null && !SAFE_IDENT.matcher(database).matches()) {
            throw new IllegalArgumentException("TableRef database name contains unsafe characters: " + database);
        }
    }

    /**
     * Creates a table reference without a database qualifier.
     *
     * @param table the table name
     * @return a new TableRef
     */
    public static TableRef of(String table) {
        return new TableRef(null, table);
    }

    /**
     * Creates a fully qualified table reference.
     *
     * @param database the database name
     * @param table    the table name
     * @return a new TableRef
     */
    public static TableRef of(String database, String table) {
        return new TableRef(database, table);
    }

    /**
     * Returns {@code database.table} or just {@code table} if no database is set.
     *
     * @return the qualified table name string
     */
    public String qualifiedName() {
        return database != null ? database + "." + table : table;
    }
}

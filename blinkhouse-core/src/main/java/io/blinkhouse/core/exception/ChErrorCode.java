package io.blinkhouse.core.exception;

/**
 * Well-known ClickHouse server error codes referenced by the exception translator
 * and error classifier.
 *
 * <p>Source: ClickHouse {@code src/Common/ErrorCodes.cpp}.
 */
public final class ChErrorCode {

    /** Query exceeded the maximum execution time. */
    public static final int TIMEOUT_EXCEEDED = 159;

    /** Too many simultaneous queries. */
    public static final int TOO_MANY_SIMULTANEOUS_QUERIES = 202;

    /** No free connection available in the pool. */
    public static final int NO_FREE_CONNECTION = 203;

    /** TCP socket timeout. */
    public static final int SOCKET_TIMEOUT = 209;

    /** Generic network-layer error. */
    public static final int NETWORK_ERROR = 210;

    /** Query exceeded memory limit. Retryable with smaller batch. */
    public static final int MEMORY_LIMIT_EXCEEDED = 241;

    /** Too many active parts in a partition; ingest back-pressure. Retryable. */
    public static final int TOO_MANY_PARTS = 252;

    /** ZooKeeper / Keeper error on replicated tables. */
    public static final int KEEPER_EXCEPTION = 999;

    /** Unknown identifier (column, alias, etc.) in a query. */
    public static final int UNKNOWN_IDENTIFIER = 47;

    /** Type mismatch between query and schema. */
    public static final int TYPE_MISMATCH = 53;

    /** Reference to a table that does not exist. */
    public static final int UNKNOWN_TABLE = 60;

    /** SQL syntax error. */
    public static final int SYNTAX_ERROR = 62;

    /** Reference to a database that does not exist. */
    public static final int UNKNOWN_DATABASE = 81;

    /** Authentication failure. */
    public static final int UNKNOWN_USER = 192;

    private ChErrorCode() {}
}

package io.blinkhouse.core.exception;

/**
 * ClickHouse server error code constants referenced by {@link ChExceptionTranslator}
 * and {@link io.blinkhouse.core.write.ErrorClassifier}.
 */
public final class ChErrorCode {

    // --- Retryable ---
    public static final int TIMEOUT_EXCEEDED                  = 159;
    public static final int TOO_MANY_SIMULTANEOUS_QUERIES     = 202;
    public static final int NO_FREE_CONNECTION                = 203;
    public static final int SOCKET_TIMEOUT                    = 209;
    public static final int NETWORK_ERROR                     = 210;
    public static final int MEMORY_LIMIT_EXCEEDED             = 241;
    public static final int TOO_MANY_PARTS                    = 252;
    public static final int KEEPER_EXCEPTION                  = 999;

    // --- Terminal ---
    public static final int UNKNOWN_IDENTIFIER                = 47;
    public static final int TYPE_MISMATCH                     = 53;
    public static final int UNKNOWN_TABLE                     = 60;
    public static final int SYNTAX_ERROR                      = 62;
    public static final int UNKNOWN_DATABASE                  = 81;
    public static final int UNKNOWN_USER                      = 192;

    private ChErrorCode() {}
}

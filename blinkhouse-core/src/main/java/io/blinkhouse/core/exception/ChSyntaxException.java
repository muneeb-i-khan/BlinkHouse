package io.blinkhouse.core.exception;

/**
 * Thrown when ClickHouse reports a SQL syntax error (error code 62).
 */
public final class ChSyntaxException extends ChException {

    /** Constructs with the server error message. */
    public ChSyntaxException(String message) {
        super(message, ChErrorCode.SYNTAX_ERROR);
    }
}

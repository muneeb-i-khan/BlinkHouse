package io.blinkhouse.core.exception;

/** Thrown when ClickHouse reports a query syntax error (error code 62). */
public final class ChSyntaxException extends ChException {

    public ChSyntaxException(String message) {
        super(message, ChErrorCode.SYNTAX_ERROR);
    }

    public ChSyntaxException(String message, Throwable cause) {
        super(message, ChErrorCode.SYNTAX_ERROR, cause);
    }
}

package io.blinkhouse.core.exception;

/**
 * Thrown when the metadata resolver cannot map a Java type to a ClickHouse schema.
 *
 * <p>This is always a programming error — messages are deliberately verbose to
 * include the class name, field name, Java type, and a suggested fix.
 */
public final class ChMappingException extends ChException {

    /** Constructs with a descriptive message. */
    public ChMappingException(String message) {
        super(message);
    }
}

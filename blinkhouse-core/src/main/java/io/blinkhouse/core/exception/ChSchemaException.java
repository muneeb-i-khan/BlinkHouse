package io.blinkhouse.core.exception;

/**
 * Thrown when schema validation, DDL execution, or schema introspection fails.
 *
 * <p>Typical causes:
 * <ul>
 *   <li>VALIDATE mode detects drift between the entity definition and the live table</li>
 *   <li>A {@code CREATE TABLE} statement fails on the server</li>
 *   <li>An {@code ALTER TABLE} is refused because the change is destructive and
 *       {@code allowDestructive} was not set</li>
 *   <li>An engine or ORDER BY mismatch is detected (never auto-fixable)</li>
 * </ul>
 */
public final class ChSchemaException extends ChException {

    /** Constructs with a descriptive message. */
    public ChSchemaException(String message) {
        super(message);
    }

    /** Constructs with a message and the underlying server exception. */
    public ChSchemaException(String message, Throwable cause) {
        super(message, cause);
    }
}

/**
 * Exception hierarchy and ClickHouse error-code translation.
 *
 * <p>Typed exceptions enable retry classification (FR-5.5) and actionable diagnostics
 * (FR-1.10). {@code UNKNOWN} error codes are treated as TERMINAL by default — retrying
 * an unclassified error risks data duplication.
 *
 * <ul>
 *   <li>{@code ChException} — root unchecked exception</li>
 *   <li>{@code ChSyntaxException} — error code 62 SYNTAX_ERROR</li>
 *   <li>{@code ChTimeoutException} — error code 159 TIMEOUT_EXCEEDED</li>
 *   <li>{@code ChMemoryLimitException} — error code 241 MEMORY_LIMIT_EXCEEDED</li>
 *   <li>{@code ChConnectionException} — network / transport failures</li>
 *   <li>{@code ChTooManyPartsException} — error code 252 TOO_MANY_PARTS</li>
 *   <li>{@code ChMappingException} — entity or column mapping validation failure</li>
 *   <li>{@code ChSchemaException} — schema drift or DDL failure</li>
 *   <li>{@code ChExceptionTranslator} — ClickHouse error code → ChException subtypes</li>
 *   <li>{@code ChErrorCode} — constants for ClickHouse error codes</li>
 * </ul>
 */
package io.blinkhouse.core.exception;

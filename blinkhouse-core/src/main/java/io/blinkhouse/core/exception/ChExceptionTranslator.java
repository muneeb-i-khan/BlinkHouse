package io.blinkhouse.core.exception;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Translates raw ClickHouse HTTP error responses (status 4xx/5xx with a body like
 * {@code "Code: 62. DB::Exception: syntax error ..."}) into typed {@link ChException}
 * subtypes.
 *
 * <p>The error code is parsed from the response body when present; if parsing fails the
 * translator falls back to a generic {@link ChException} with the raw message.
 */
public final class ChExceptionTranslator {

    private static final Pattern CODE_PATTERN = Pattern.compile("Code:\\s*(\\d+)\\.");

    /** Translates an HTTP response body string + HTTP status into a typed exception. */
    public ChException translate(String responseBody, int httpStatus) {
        int code = extractCode(responseBody);
        String msg = responseBody != null ? responseBody.trim() : "HTTP " + httpStatus;
        return toTyped(code, msg, null);
    }

    /** Translates a low-level network/IO exception (no server response). */
    public ChException translateNetworkError(Throwable cause) {
        return new ChConnectionException("ClickHouse network error: " + cause.getMessage(), cause);
    }

    /** Translates an already-parsed error code + message into a typed exception. */
    public ChException translate(int errorCode, String message) {
        return toTyped(errorCode, message, null);
    }

    private ChException toTyped(int code, String message, Throwable cause) {
        return switch (code) {
            case ChErrorCode.SYNTAX_ERROR          -> new ChSyntaxException(message, cause);
            case ChErrorCode.TIMEOUT_EXCEEDED,
                 ChErrorCode.SOCKET_TIMEOUT        -> new ChTimeoutException(message, code, cause);
            case ChErrorCode.MEMORY_LIMIT_EXCEEDED -> new ChMemoryLimitException(message, cause);
            case ChErrorCode.NETWORK_ERROR,
                 ChErrorCode.NO_FREE_CONNECTION    -> new ChConnectionException(message, code, cause);
            case ChErrorCode.TOO_MANY_PARTS        -> new ChTooManyPartsException(message, cause);
            default                                -> new ChException(message, code, cause);
        };
    }

    private static int extractCode(String body) {
        if (body == null || body.isEmpty()) {
            return -1;
        }
        Matcher m = CODE_PATTERN.matcher(body);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return -1;
    }
}

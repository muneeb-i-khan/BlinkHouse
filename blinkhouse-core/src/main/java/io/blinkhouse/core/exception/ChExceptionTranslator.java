package io.blinkhouse.core.exception;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Translates raw ClickHouse HTTP error responses and network exceptions into the
 * typed {@link ChException} hierarchy.
 *
 * <p>ClickHouse error bodies have the form:
 * <pre>
 *   Code: 241. DB::Exception: Memory limit (total) exceeded: ...
 * </pre>
 */
public final class ChExceptionTranslator {

    private static final Pattern CODE_PATTERN = Pattern.compile("Code:\\s*(\\d+)\\.");

    private ChExceptionTranslator() {}

    /**
     * Translates an HTTP response body and status code into the appropriate
     * {@link ChException} subtype.
     *
     * @param body       the raw response body from ClickHouse
     * @param httpStatus the HTTP status code (e.g. 500, 503)
     * @return a typed exception
     */
    public static ChException translate(String body, int httpStatus) {
        int code = extractCode(body);
        return translate(code, body);
    }

    /**
     * Translates a network-level throwable (connection refused, timeout, etc.)
     * into a {@link ChConnectionException} or {@link ChTimeoutException}.
     *
     * @param cause the underlying network exception
     * @return a typed connection or timeout exception
     */
    public static ChException translateNetworkError(Throwable cause) {
        String message = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
        if (isTimeout(cause)) {
            return new ChTimeoutException("Network timeout: " + message, cause);
        }
        return new ChConnectionException("Network error: " + message, cause);
    }

    /**
     * Translates a known error code and message into the appropriate typed exception.
     *
     * @param code    the ClickHouse error code ({@code -1} if unknown)
     * @param message the error message
     * @return a typed exception
     */
    public static ChException translate(int code, String message) {
        switch (code) {
            case ChErrorCode.SYNTAX_ERROR:
                return new ChSyntaxException(message);
            case ChErrorCode.TIMEOUT_EXCEEDED:
            case ChErrorCode.SOCKET_TIMEOUT:
                return new ChTimeoutException(message, code);
            case ChErrorCode.MEMORY_LIMIT_EXCEEDED:
                return new ChMemoryLimitException(message);
            case ChErrorCode.TOO_MANY_PARTS:
                return new ChTooManyPartsException(message);
            case ChErrorCode.NO_FREE_CONNECTION:
            case ChErrorCode.NETWORK_ERROR:
                return new ChConnectionException(message, code);
            default:
                return new ChException(message, code);
        }
    }

    private static int extractCode(String body) {
        if (body == null) {
            return -1;
        }
        Matcher m = CODE_PATTERN.matcher(body);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }

    private static boolean isTimeout(Throwable t) {
        if (t == null) {
            return false;
        }
        String name = t.getClass().getSimpleName().toLowerCase();
        return name.contains("timeout") || name.contains("sotimeout");
    }
}

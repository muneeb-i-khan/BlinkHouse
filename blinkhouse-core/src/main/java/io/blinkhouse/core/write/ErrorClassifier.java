package io.blinkhouse.core.write;

import io.blinkhouse.core.exception.ChErrorCode;
import io.blinkhouse.core.exception.ChException;

import java.util.Set;

/**
 * Classifies a {@link ChException} as retryable or terminal so the
 * {@link BatchWriter} flusher can decide whether to retry or dead-letter a batch.
 *
 * <p>Two codes get special treatment — {@code MEMORY_LIMIT_EXCEEDED} (241) and
 * {@code TOO_MANY_PARTS} (252) — because they warrant halving the batch size on
 * retry rather than just backing off with the same payload.
 *
 * <p>All unknown codes default to {@code TERMINAL} (conservative default — see HLD §9
 * and LLD §9.2).
 */
public final class ErrorClassifier {

    /** Outcome of classifying a failure. */
    public enum Classification {
        /** Retry with exponential backoff; keep the same batch size. */
        RETRYABLE,
        /** Retry with exponential backoff AND halve the batch size. */
        RETRYABLE_HALVE_BATCH,
        /** Do not retry; hand batch to the {@link BatchFailureHandler}. */
        TERMINAL
    }

    private static final Set<Integer> RETRYABLE_CODES = Set.of(
            ChErrorCode.TIMEOUT_EXCEEDED,
            ChErrorCode.TOO_MANY_SIMULTANEOUS_QUERIES,
            ChErrorCode.NO_FREE_CONNECTION,
            ChErrorCode.SOCKET_TIMEOUT,
            ChErrorCode.NETWORK_ERROR,
            ChErrorCode.KEEPER_EXCEPTION
    );

    private static final Set<Integer> RETRYABLE_HALVE_CODES = Set.of(
            ChErrorCode.MEMORY_LIMIT_EXCEEDED,
            ChErrorCode.TOO_MANY_PARTS
    );

    /** Classifies the given exception. */
    public Classification classify(ChException ex) {
        int code = ex.getErrorCode();
        if (RETRYABLE_HALVE_CODES.contains(code)) {
            return Classification.RETRYABLE_HALVE_BATCH;
        }
        if (RETRYABLE_CODES.contains(code)) {
            return Classification.RETRYABLE;
        }
        return Classification.TERMINAL;
    }

    /** Classifies a raw network/IO exception (always retryable). */
    public Classification classifyNetworkError() {
        return Classification.RETRYABLE;
    }
}

package io.blinkhouse.core.write;

import io.blinkhouse.core.exception.ChErrorCode;
import io.blinkhouse.core.exception.ChException;
import java.util.Set;

/**
 * Classifies a {@link ChException} into a retry strategy for the batch writer.
 *
 * <p>Classification drives the retry loop in {@link BatchWriter}:
 * <ul>
 *   <li>{@link Classification#RETRYABLE} — retry with the same batch</li>
 *   <li>{@link Classification#RETRYABLE_HALVE_BATCH} — retry with half the batch,
 *       requeue the other half</li>
 *   <li>{@link Classification#TERMINAL} — send to dead letter; do not retry</li>
 * </ul>
 */
public final class ErrorClassifier {

    /** The retry strategy for a given exception. */
    public enum Classification {

        /** Retry the full batch after a delay. */
        RETRYABLE,

        /** Retry the first half of the batch; requeue the second half. */
        RETRYABLE_HALVE_BATCH,

        /** Do not retry — deliver to the failure handler. */
        TERMINAL
    }

    private static final Set<Integer> RETRYABLE_HALVE_CODES = Set.of(
        ChErrorCode.MEMORY_LIMIT_EXCEEDED,
        ChErrorCode.TOO_MANY_PARTS
    );

    private static final Set<Integer> RETRYABLE_CODES = Set.of(
        ChErrorCode.TIMEOUT_EXCEEDED,
        ChErrorCode.TOO_MANY_SIMULTANEOUS_QUERIES,
        ChErrorCode.NO_FREE_CONNECTION,
        ChErrorCode.SOCKET_TIMEOUT,
        ChErrorCode.NETWORK_ERROR,
        ChErrorCode.KEEPER_EXCEPTION
    );

    private ErrorClassifier() {}

    /**
     * Classifies a {@link ChException} for the retry loop.
     *
     * @param ex the exception to classify
     * @return the classification
     */
    public static Classification classify(ChException ex) {
        int code = ex.getErrorCode();
        if (RETRYABLE_HALVE_CODES.contains(code)) {
            return Classification.RETRYABLE_HALVE_BATCH;
        }
        if (RETRYABLE_CODES.contains(code)) {
            return Classification.RETRYABLE;
        }
        return Classification.TERMINAL;
    }
}

package io.blinkhouse.core.write;

import io.blinkhouse.core.exception.ChErrorCode;
import io.blinkhouse.core.exception.ChException;
import org.junit.jupiter.api.Test;

import static io.blinkhouse.core.write.ErrorClassifier.Classification.RETRYABLE;
import static io.blinkhouse.core.write.ErrorClassifier.Classification.RETRYABLE_HALVE_BATCH;
import static io.blinkhouse.core.write.ErrorClassifier.Classification.TERMINAL;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ErrorClassifier}.
 */
class ErrorClassifierTest {

    @Test
    void memoryLimitExceeded_isRetryableHalveBatch() {
        ChException ex = new ChException("memory limit", ChErrorCode.MEMORY_LIMIT_EXCEEDED);
        assertThat(ErrorClassifier.classify(ex)).isEqualTo(RETRYABLE_HALVE_BATCH);
    }

    @Test
    void tooManyParts_isRetryableHalveBatch() {
        ChException ex = new ChException("too many parts", ChErrorCode.TOO_MANY_PARTS);
        assertThat(ErrorClassifier.classify(ex)).isEqualTo(RETRYABLE_HALVE_BATCH);
    }

    @Test
    void timeoutExceeded_isRetryable() {
        ChException ex = new ChException("timeout", ChErrorCode.TIMEOUT_EXCEEDED);
        assertThat(ErrorClassifier.classify(ex)).isEqualTo(RETRYABLE);
    }

    @Test
    void tooManySimultaneousQueries_isRetryable() {
        ChException ex = new ChException("too many queries", ChErrorCode.TOO_MANY_SIMULTANEOUS_QUERIES);
        assertThat(ErrorClassifier.classify(ex)).isEqualTo(RETRYABLE);
    }

    @Test
    void noFreeConnection_isRetryable() {
        ChException ex = new ChException("no connection", ChErrorCode.NO_FREE_CONNECTION);
        assertThat(ErrorClassifier.classify(ex)).isEqualTo(RETRYABLE);
    }

    @Test
    void socketTimeout_isRetryable() {
        ChException ex = new ChException("socket timeout", ChErrorCode.SOCKET_TIMEOUT);
        assertThat(ErrorClassifier.classify(ex)).isEqualTo(RETRYABLE);
    }

    @Test
    void networkError_isRetryable() {
        ChException ex = new ChException("network error", ChErrorCode.NETWORK_ERROR);
        assertThat(ErrorClassifier.classify(ex)).isEqualTo(RETRYABLE);
    }

    @Test
    void keeperException_isRetryable() {
        ChException ex = new ChException("keeper error", ChErrorCode.KEEPER_EXCEPTION);
        assertThat(ErrorClassifier.classify(ex)).isEqualTo(RETRYABLE);
    }

    @Test
    void syntaxError_isTerminal() {
        ChException ex = new ChException("syntax error", ChErrorCode.SYNTAX_ERROR);
        assertThat(ErrorClassifier.classify(ex)).isEqualTo(TERMINAL);
    }

    @Test
    void unknownTable_isTerminal() {
        ChException ex = new ChException("unknown table", ChErrorCode.UNKNOWN_TABLE);
        assertThat(ErrorClassifier.classify(ex)).isEqualTo(TERMINAL);
    }

    @Test
    void unknownDatabase_isTerminal() {
        ChException ex = new ChException("unknown db", ChErrorCode.UNKNOWN_DATABASE);
        assertThat(ErrorClassifier.classify(ex)).isEqualTo(TERMINAL);
    }

    @Test
    void typeMismatch_isTerminal() {
        ChException ex = new ChException("type mismatch", ChErrorCode.TYPE_MISMATCH);
        assertThat(ErrorClassifier.classify(ex)).isEqualTo(TERMINAL);
    }

    @Test
    void unknownCode_isTerminal() {
        ChException ex = new ChException("unknown", 9999);
        assertThat(ErrorClassifier.classify(ex)).isEqualTo(TERMINAL);
    }
}

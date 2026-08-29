package io.blinkhouse.core.write;

import io.blinkhouse.core.exception.ChErrorCode;
import io.blinkhouse.core.exception.ChException;
import org.junit.jupiter.api.Test;

import static io.blinkhouse.core.write.ErrorClassifier.Classification.*;
import static org.assertj.core.api.Assertions.assertThat;

class ErrorClassifierTest {

    private final ErrorClassifier classifier = new ErrorClassifier();

    @Test
    void timeoutIsRetryable() {
        assertThat(classify(ChErrorCode.TIMEOUT_EXCEEDED)).isEqualTo(RETRYABLE);
    }

    @Test
    void tooManySimultaneousQueriesIsRetryable() {
        assertThat(classify(ChErrorCode.TOO_MANY_SIMULTANEOUS_QUERIES)).isEqualTo(RETRYABLE);
    }

    @Test
    void networkErrorIsRetryable() {
        assertThat(classify(ChErrorCode.NETWORK_ERROR)).isEqualTo(RETRYABLE);
    }

    @Test
    void socketTimeoutIsRetryable() {
        assertThat(classify(ChErrorCode.SOCKET_TIMEOUT)).isEqualTo(RETRYABLE);
    }

    @Test
    void keeperExceptionIsRetryable() {
        assertThat(classify(ChErrorCode.KEEPER_EXCEPTION)).isEqualTo(RETRYABLE);
    }

    @Test
    void memoryLimitExceededIsRetryableHalveBatch() {
        assertThat(classify(ChErrorCode.MEMORY_LIMIT_EXCEEDED)).isEqualTo(RETRYABLE_HALVE_BATCH);
    }

    @Test
    void tooManyPartsIsRetryableHalveBatch() {
        assertThat(classify(ChErrorCode.TOO_MANY_PARTS)).isEqualTo(RETRYABLE_HALVE_BATCH);
    }

    @Test
    void syntaxErrorIsTerminal() {
        assertThat(classify(ChErrorCode.SYNTAX_ERROR)).isEqualTo(TERMINAL);
    }

    @Test
    void unknownTableIsTerminal() {
        assertThat(classify(ChErrorCode.UNKNOWN_TABLE)).isEqualTo(TERMINAL);
    }

    @Test
    void typeMismatchIsTerminal() {
        assertThat(classify(ChErrorCode.TYPE_MISMATCH)).isEqualTo(TERMINAL);
    }

    @Test
    void unknownUserIsTerminal() {
        assertThat(classify(ChErrorCode.UNKNOWN_USER)).isEqualTo(TERMINAL);
    }

    @Test
    void unknownCodeDefaultsToTerminal() {
        assertThat(classify(99999)).isEqualTo(TERMINAL);
    }

    @Test
    void networkErrorClassifierReturnsRetryable() {
        assertThat(classifier.classifyNetworkError()).isEqualTo(RETRYABLE);
    }

    private ErrorClassifier.Classification classify(int code) {
        return classifier.classify(new ChException("test", code));
    }
}

package io.blinkhouse.core.write;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RetryPolicy}.
 */
class RetryPolicyTest {

    @Test
    void attempt0_returnsZeroDelay() {
        RetryPolicy policy = RetryPolicy.defaults();
        assertThat(policy.delayFor(0)).isEqualTo(Duration.ZERO);
    }

    @Test
    void attempt1_returnsPositiveDelay() {
        RetryPolicy policy = RetryPolicy.defaults();
        Duration delay = policy.delayFor(1);
        assertThat(delay).isGreaterThanOrEqualTo(Duration.ZERO);
        assertThat(delay).isLessThanOrEqualTo(policy.initialDelay());
    }

    @Test
    void delayIsCappedAtMaxDelay() {
        RetryPolicy policy = RetryPolicy.defaults();
        for (int i = 0; i < 20; i++) {
            Duration delay = policy.delayFor(i);
            assertThat(delay).isLessThanOrEqualTo(policy.maxDelay());
        }
    }

    @Test
    void hasNextAttempt_respectsMaxAttempts() {
        RetryPolicy policy = new RetryPolicy(3, Duration.ofMillis(100), 2.0, Duration.ofSeconds(10));
        assertThat(policy.hasNextAttempt(0)).isTrue();
        assertThat(policy.hasNextAttempt(1)).isTrue();
        assertThat(policy.hasNextAttempt(2)).isFalse();
    }
}

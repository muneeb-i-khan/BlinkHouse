package io.blinkhouse.core.write;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RetryPolicyTest {

    @Test
    void firstAttemptHasZeroDelay() {
        RetryPolicy policy = RetryPolicy.defaults();
        assertThat(policy.delayFor(0)).isEqualTo(Duration.ZERO);
    }

    @Test
    void subsequentAttemptsHavePositiveDelay() {
        RetryPolicy policy = new RetryPolicy(6, Duration.ofMillis(100), 2.0, Duration.ofSeconds(30));
        // delay is jittered so we can only assert it's in [0, max]
        for (int i = 1; i <= 4; i++) {
            Duration d = policy.delayFor(i);
            assertThat(d.toMillis())
                    .as("attempt " + i + " delay should be non-negative")
                    .isGreaterThanOrEqualTo(0);
            assertThat(d.compareTo(Duration.ofSeconds(30)))
                    .as("attempt " + i + " delay should not exceed maxDelay")
                    .isLessThanOrEqualTo(0);
        }
    }

    @Test
    void hasNextAttemptRespectsBound() {
        RetryPolicy policy = new RetryPolicy(3, Duration.ofMillis(100), 2.0, Duration.ofSeconds(30));
        assertThat(policy.hasNextAttempt(0)).isTrue();
        assertThat(policy.hasNextAttempt(2)).isTrue();
        assertThat(policy.hasNextAttempt(3)).isFalse();
    }

    @Test
    void delayIsCappeAtMaxDelay() {
        RetryPolicy policy = new RetryPolicy(10, Duration.ofMillis(1000), 10.0, Duration.ofMillis(500));
        // After several doublings, jittered delay must still be ≤ maxDelay
        for (int i = 1; i <= 10; i++) {
            assertThat(policy.delayFor(i).toMillis())
                    .as("attempt " + i + " must not exceed maxDelay=500ms")
                    .isLessThanOrEqualTo(500);
        }
    }
}

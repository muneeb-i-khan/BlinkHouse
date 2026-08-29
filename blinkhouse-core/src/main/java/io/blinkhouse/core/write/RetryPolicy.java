package io.blinkhouse.core.write;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Exponential back-off with full jitter for {@link BatchWriter} retry loops.
 *
 * <p>Delay formula: {@code min(maxDelay, initialDelay * multiplier^attempt) * random[0,1]}
 * — this is the "full jitter" approach from the AWS Builder's Library, which produces
 * lower mean latency and lower collision rate than capped exponential alone.
 *
 * @param maxAttempts  maximum number of attempts before the batch is dead-lettered
 * @param initialDelay base delay for the first retry
 * @param multiplier   exponential growth factor per attempt
 * @param maxDelay     upper cap on the computed delay before jitter
 */
public record RetryPolicy(
        int maxAttempts,
        Duration initialDelay,
        double multiplier,
        Duration maxDelay) {

    /**
     * Default policy: 6 attempts, 100ms initial, 2× multiplier, 30s cap.
     *
     * @return the default retry policy
     */
    public static RetryPolicy defaults() {
        return new RetryPolicy(6, Duration.ofMillis(100), 2.0, Duration.ofSeconds(30));
    }

    /**
     * Computes the jittered delay for the given attempt number (0-indexed).
     *
     * <p>Attempt 0 returns zero — the first attempt is always immediate.
     *
     * @param attempt the zero-based attempt number
     * @return the duration to wait before the next attempt
     */
    public Duration delayFor(int attempt) {
        if (attempt <= 0) {
            return Duration.ZERO;
        }
        double capped = Math.min(
            maxDelay.toMillis(),
            initialDelay.toMillis() * Math.pow(multiplier, attempt - 1)
        );
        long jittered = (long) (capped * ThreadLocalRandom.current().nextDouble());
        return Duration.ofMillis(jittered);
    }

    /**
     * Returns {@code true} if there are more retry attempts available.
     *
     * @param attempt the zero-based attempt number just completed
     * @return whether another attempt should be made
     */
    public boolean hasNextAttempt(int attempt) {
        return attempt < maxAttempts - 1;
    }
}

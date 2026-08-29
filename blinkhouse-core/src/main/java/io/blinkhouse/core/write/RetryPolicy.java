package io.blinkhouse.core.write;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Exponential back-off with full jitter for the {@link BatchWriter} flush retry loop.
 *
 * <p>Delay formula: {@code min(maxDelay, initialDelay * multiplier^attempt) * random[0,1]}.
 * Full jitter (multiplying by a uniform random value) avoids retry storms when many
 * flusher threads fail simultaneously.
 */
public record RetryPolicy(
        int maxAttempts,
        Duration initialDelay,
        double multiplier,
        Duration maxDelay
) {

    /** Sensible production defaults: 6 attempts, 100 ms base, 2× multiplier, 30 s cap. */
    public static RetryPolicy defaults() {
        return new RetryPolicy(6, Duration.ofMillis(100), 2.0, Duration.ofSeconds(30));
    }

    /**
     * Computes the delay before attempt {@code attempt} (0-indexed).
     * Returns {@link Duration#ZERO} for attempt 0 (first try — no backoff needed).
     */
    public Duration delayFor(int attempt) {
        if (attempt <= 0) {
            return Duration.ZERO;
        }
        double base = initialDelay.toMillis() * Math.pow(multiplier, attempt - 1);
        double capped = Math.min(base, maxDelay.toMillis());
        long jittered = (long) (capped * ThreadLocalRandom.current().nextDouble());
        return Duration.ofMillis(jittered);
    }

    /** Returns {@code true} if there are more attempts available after {@code attempt}. */
    public boolean hasNextAttempt(int attempt) {
        return attempt < maxAttempts;
    }
}

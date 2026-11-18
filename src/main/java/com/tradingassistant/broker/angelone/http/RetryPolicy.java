package com.tradingassistant.broker.angelone.http;

import com.tradingassistant.broker.config.AngelOneProperties;

import java.time.Duration;

/**
 * Bounded exponential-backoff policy for safe, read-only broker requests.
 * Authentication, token refresh and logout use {@link #none()} because
 * repeating them can create duplicate sessions or invalidate rotated tokens.
 */
public record RetryPolicy(int maxRetries, Duration baseDelay, Duration maxDelay) {

    public static RetryPolicy none() {
        return new RetryPolicy(0, Duration.ZERO, Duration.ZERO);
    }

    public static RetryPolicy safe(AngelOneProperties properties) {
        return new RetryPolicy(properties.maxRetries(), properties.retryBaseDelay(), properties.retryMaxDelay());
    }

    public boolean allowsRetry(int completedAttempts) {
        return completedAttempts < maxRetries;
    }

    public Duration delay(int completedAttempts) {
        long multiplier = 1L << Math.min(completedAttempts, 10);
        long delayMillis;
        try {
            delayMillis = Math.multiplyExact(baseDelay.toMillis(), multiplier);
        } catch (ArithmeticException exception) {
            delayMillis = maxDelay.toMillis();
        }
        return Duration.ofMillis(Math.min(delayMillis, maxDelay.toMillis()));
    }
}

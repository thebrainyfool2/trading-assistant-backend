package com.tradingassistant.pipeline.session;

import java.time.Duration;

/**
 * Limits for the session registry.
 *
 * @param idleTimeout how long a session may go without an update before expiry;
 *                    {@link Duration#ZERO} disables idle expiry
 * @param maxSessions hard cap on concurrently active sessions
 */
public record SessionRegistryPolicy(Duration idleTimeout, int maxSessions) {

    public SessionRegistryPolicy {
        if (idleTimeout == null || idleTimeout.isNegative()) {
            throw new IllegalArgumentException("idleTimeout must be zero or positive");
        }
        if (maxSessions < 1) {
            throw new IllegalArgumentException("maxSessions must be positive");
        }
    }

    /**
     * Thirty idle minutes and two thousand sessions: enough for roughly a
     * thousand instruments on two timeframes, with headroom.
     */
    public static SessionRegistryPolicy defaults() {
        return new SessionRegistryPolicy(Duration.ofMinutes(30), 2_000);
    }

    public static SessionRegistryPolicy unlimited() {
        return new SessionRegistryPolicy(Duration.ZERO, Integer.MAX_VALUE);
    }

    public boolean idleExpiryEnabled() {
        return !idleTimeout.isZero();
    }
}

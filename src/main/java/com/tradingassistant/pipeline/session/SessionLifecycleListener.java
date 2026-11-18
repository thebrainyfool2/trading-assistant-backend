package com.tradingassistant.pipeline.session;

/**
 * Observes session lifecycle transitions without coupling the registry to the
 * event model. The application wiring attaches an adapter that publishes
 * {@code SessionStartedEvent} and {@code SessionExpiredEvent}.
 *
 * <p>Implementations must be fast and must not throw: the registry calls them
 * while it is creating or releasing a session.</p>
 */
public interface SessionLifecycleListener {

    default void onSessionStarted(IndicatorSession session) {
    }

    default void onSessionExpired(IndicatorSession session, String reason) {
    }

    /**
     * A listener that ignores every transition.
     */
    static SessionLifecycleListener noop() {
        return new SessionLifecycleListener() {
        };
    }
}

package com.tradingassistant.pipeline.session;

/**
 * Lifecycle state of an indicator session.
 *
 * <p>{@link #ACTIVE} sessions accept candles. {@link #EXPIRED} is terminal: the
 * registry has released the session, and continuing to use the instance is a
 * programming error rather than a silent no-op.</p>
 */
public enum IndicatorSessionState {
    ACTIVE,
    EXPIRED
}

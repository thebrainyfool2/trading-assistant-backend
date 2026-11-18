package com.tradingassistant.intelligence.domain;

/**
 * Phase of the exchange session containing the assessed candle.
 */
public enum SessionPhase {

    /** Before the open of a session that starts later on the same market date. */
    PRE_OPEN,

    /** Inside the session, within the configured opening window. */
    OPENING,

    /** Inside the session, outside the opening and closing windows. */
    REGULAR,

    /** Inside the session, within the configured closing window. */
    CLOSING,

    /** After the session, including weekends and holidays. */
    AFTER_MARKET,

    /** The phase could not be resolved. */
    UNKNOWN
}

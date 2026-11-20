package com.tradingassistant.platform.trade;

/** Outcome of comparing the live option premium with the recorded boundaries. */
public enum PremiumStatus {
    NO_QUOTE,
    HOLDING,
    TARGET_HIT,
    STOP_HIT,
    EXPIRED,
    CLOSED
}

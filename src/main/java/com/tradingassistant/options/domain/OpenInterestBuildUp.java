package com.tradingassistant.options.domain;

/**
 * Positioning described by the combination of an option's own price change and
 * its open-interest change. These are descriptions of positioning, never
 * signals.
 */
public enum OpenInterestBuildUp {
    LONG_BUILD_UP,
    SHORT_BUILD_UP,
    LONG_UNWINDING,
    SHORT_COVERING,
    NEUTRAL,
    UNKNOWN
}

package com.tradingassistant.options.domain;

/**
 * Cadence of an expiry within its calendar month. The monthly expiry is the last
 * expiry listed for that month; every other expiry in the month is weekly.
 */
public enum ExpiryKind {
    WEEKLY,
    MONTHLY
}

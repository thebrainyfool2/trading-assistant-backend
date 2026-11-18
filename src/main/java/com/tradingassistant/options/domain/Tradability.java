package com.tradingassistant.options.domain;

/**
 * Descriptive tradability band derived from the liquidity assessment. It
 * describes how easily the quoted legs could be transacted; it says nothing
 * about whether they should be.
 */
public enum Tradability {
    HIGH,
    MODERATE,
    LOW,
    UNKNOWN
}

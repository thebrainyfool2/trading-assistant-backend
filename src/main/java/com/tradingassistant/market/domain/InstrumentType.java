package com.tradingassistant.market.domain;

/**
 * Broad instrument category used by the application. Provider-specific codes
 * are translated by the adapter-side mapper; consumers should use this enum.
 */
public enum InstrumentType {
    EQUITY,
    FUTURE,
    OPTION,
    INDEX,
    CURRENCY,
    COMMODITY,
    OTHER
}

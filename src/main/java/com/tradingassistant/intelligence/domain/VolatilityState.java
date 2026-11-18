package com.tradingassistant.intelligence.domain;

/**
 * Objective description of whether the trading range is widening or narrowing.
 */
public enum VolatilityState {
    EXPANDING,
    CONTRACTING,
    STABLE,
    UNKNOWN
}

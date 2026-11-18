package com.tradingassistant.options.domain;

/**
 * Whether the implied-volatility level is expanding, contracting or stable.
 */
public enum VolatilityState {
    EXPANDING,
    CONTRACTING,
    STABLE,
    UNKNOWN
}

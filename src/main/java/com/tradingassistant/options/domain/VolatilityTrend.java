package com.tradingassistant.options.domain;

/**
 * Direction of implied-volatility change against the previous observation.
 */
public enum VolatilityTrend {
    RISING,
    FALLING,
    FLAT,
    UNKNOWN
}

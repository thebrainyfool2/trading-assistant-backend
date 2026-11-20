package com.tradingassistant.recommendation.config;

/**
 * Relative strike selection requested by configuration. The offset itself is
 * configured separately; this enum never contains a fixed strike distance.
 */
public enum StrikeMode {
    ATM,
    ITM,
    OTM
}

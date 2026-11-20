package com.tradingassistant.recommendation.domain;

/**
 * Direction supported by a scorer. A neutral score can still be high when it
 * describes the quality of the market rather than a direction.
 */
public enum DirectionalBias {
    BULLISH,
    BEARISH,
    NEUTRAL,
    UNKNOWN
}

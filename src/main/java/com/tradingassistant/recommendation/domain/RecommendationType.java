package com.tradingassistant.recommendation.domain;

/**
 * The only recommendation outcomes supported in Phase 14. No sell, short,
 * spread, straddle, strangle or other multi-leg structure exists.
 */
public enum RecommendationType {
    BUY_CALL,
    BUY_PUT,
    NO_TRADE
}

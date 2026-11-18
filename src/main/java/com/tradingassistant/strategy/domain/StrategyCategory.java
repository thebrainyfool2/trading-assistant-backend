package com.tradingassistant.strategy.domain;

/**
 * Descriptive grouping of a strategy definition. The category never influences
 * evaluation; it labels what kind of conditions the strategy contains.
 */
public enum StrategyCategory {
    TREND,
    MOMENTUM,
    BREAKOUT,
    REVERSAL,
    VOLATILITY,
    OPTIONS,
    INTRADAY,
    SWING,
    CUSTOM
}

package com.tradingassistant.strategy.domain;

/**
 * Inputs a strategy definition declares it needs.
 *
 * <p>Strict evaluation uses the declaration to decide whether a strategy can be
 * evaluated at all, instead of letting every rule rediscover the same absence.
 * The declaration is descriptive metadata; it never changes a rule's meaning.</p>
 */
public enum StrategyInput {
    MARKET_ASSESSMENT,
    OPTIONS_ASSESSMENT,
    CANDLE,
    INDICATORS
}

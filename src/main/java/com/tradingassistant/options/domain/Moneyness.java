package com.tradingassistant.options.domain;

/**
 * Position of a strike relative to the spot price for one option type.
 * Describes geometry only; it is not a valuation or a recommendation.
 */
public enum Moneyness {
    DEEP_ITM,
    ITM,
    ATM,
    OTM,
    DEEP_OTM
}

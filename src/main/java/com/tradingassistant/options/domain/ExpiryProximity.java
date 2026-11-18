package com.tradingassistant.options.domain;

/**
 * Position of an expiry inside the chain, ordered by date.
 */
public enum ExpiryProximity {
    NEAREST,
    NEXT,
    FAR
}

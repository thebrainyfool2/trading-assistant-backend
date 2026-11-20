package com.tradingassistant.recommendation.config;

/**
 * Which allowed expiry to use. The chain supplies the ordered dates; the policy
 * never invents an expiry.
 */
public enum ExpirySelection {
    NEAREST,
    NEXT,
    FAR
}

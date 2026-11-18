package com.tradingassistant.intelligence.domain;

/**
 * Objective description of how much volume is taking part in the move. Not a
 * recommendation and not a liquidity or position measure.
 */
public enum ParticipationState {
    STRONG,
    AVERAGE,
    WEAK,
    UNKNOWN
}

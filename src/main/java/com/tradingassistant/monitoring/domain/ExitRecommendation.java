package com.tradingassistant.monitoring.domain;

/**
 * Recommendation only. The owner always exits manually outside the application.
 */
public enum ExitRecommendation {
    HOLD,
    WATCH_CLOSELY,
    PREPARE_EXIT,
    EXIT_NOW
}
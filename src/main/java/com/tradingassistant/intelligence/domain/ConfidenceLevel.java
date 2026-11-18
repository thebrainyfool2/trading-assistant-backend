package com.tradingassistant.intelligence.domain;

/**
 * Descriptive confidence level for an assessment. Deliberately an enum rather
 * than a number: the analysis layer reports which inputs were available, not a
 * market score.
 */
public enum ConfidenceLevel {
    HIGH,
    MEDIUM,
    LOW,
    UNKNOWN
}

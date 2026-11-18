package com.tradingassistant.execution.domain;

/**
 * Outcome of a single execution rule.
 */
public enum ExecutionRuleStatus {
    PASSED,
    FAILED,
    INSUFFICIENT_DATA,
    UNKNOWN
}

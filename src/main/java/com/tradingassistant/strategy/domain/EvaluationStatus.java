package com.tradingassistant.strategy.domain;

/**
 * Outcome of evaluating a strategy or one of its rules.
 *
 * <p>{@code PASSED} means every condition evaluated to true, {@code FAILED}
 * means at least one condition evaluated to false, {@code PARTIAL} means some
 * conditions were true while others had no usable data, and
 * {@code INSUFFICIENT_DATA} means no condition could be decided. {@code UNKNOWN}
 * is used when a condition produced no decision, for example after an unexpected
 * rule failure that was contained instead of propagated.</p>
 */
public enum EvaluationStatus {
    PASSED,
    FAILED,
    PARTIAL,
    INSUFFICIENT_DATA,
    UNKNOWN
}

package com.tradingassistant.execution.domain;

/**
 * Outcome of one execution attempt.
 */
public enum ExecutionStatus {
    /** The broker filled the whole quantity. */
    COMPLETED,

    /** The broker filled part of the quantity. */
    PARTIAL,

    /** The order is with the broker but not filled yet. */
    PENDING,

    /** Validation or the broker refused the order. */
    REJECTED,

    /** The broker could not be reached or failed. */
    FAILED,

    /** The order was cancelled or expired. */
    CANCELLED
}

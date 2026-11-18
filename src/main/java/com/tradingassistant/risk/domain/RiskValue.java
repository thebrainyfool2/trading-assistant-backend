package com.tradingassistant.risk.domain;

import java.math.BigDecimal;

/**
 * Numeric guards shared by the risk models. They reject structurally invalid
 * values so a record can never hold a negative distance, quantity or exposure.
 */
final class RiskValue {

    private RiskValue() {
    }

    static BigDecimal requireNonNegative(BigDecimal value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        if (value.signum() < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
        return value;
    }
}

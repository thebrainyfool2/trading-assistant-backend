package com.tradingassistant.execution.domain;

import com.tradingassistant.intelligence.domain.MarketAssessment;
import com.tradingassistant.risk.domain.RiskAssessment;

import java.util.Objects;

/**
 * One order request together with the immutable descriptions it was derived from.
 *
 * <p>Bundling them keeps the engine's inputs explicit: the order supplies the
 * direction, the risk assessment supplies the size and protective distance, and
 * the market assessment supplies the session context.</p>
 *
 * @param order            order the caller wants placed
 * @param riskAssessment   risk boundaries the order must respect
 * @param marketAssessment market description of the same instant
 */
public record ExecutionRequest(
        OrderRequest order,
        RiskAssessment riskAssessment,
        MarketAssessment marketAssessment) {

    public ExecutionRequest {
        order = Objects.requireNonNull(order, "order must not be null");
        riskAssessment = Objects.requireNonNull(riskAssessment, "riskAssessment must not be null");
        marketAssessment = Objects.requireNonNull(marketAssessment,
                "marketAssessment must not be null");
        if (!order.strategyId().equals(riskAssessment.strategyId())) {
            throw new IllegalArgumentException(
                    "strategyId of the order must match the risk assessment");
        }
        if (!order.instrument().equals(riskAssessment.instrument())) {
            throw new IllegalArgumentException(
                    "instrument of the order must match the risk assessment");
        }
        if (!order.timestamp().equals(riskAssessment.timestamp())) {
            throw new IllegalArgumentException(
                    "timestamp of the order must match the risk assessment");
        }
        if (marketAssessment.timestamp() == null
                || !marketAssessment.timestamp().equals(order.timestamp())) {
            throw new IllegalArgumentException(
                    "timestamp of the market assessment must match the order");
        }
    }
}

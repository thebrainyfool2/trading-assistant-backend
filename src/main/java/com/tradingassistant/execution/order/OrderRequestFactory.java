package com.tradingassistant.execution.order;

import com.tradingassistant.execution.domain.OrderIntent;
import com.tradingassistant.execution.domain.OrderRequest;
import com.tradingassistant.execution.domain.OrderSide;
import com.tradingassistant.execution.exception.ExecutionValidationException;
import com.tradingassistant.risk.domain.RiskAssessment;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Turns a caller's intent plus a risk assessment into an order request.
 *
 * <p>The quantity and the protective distance come from the assessment; the
 * direction, order type and trigger prices come from the intent. The factory never
 * invents a side and never resizes beyond the assessment: if the assessment has no
 * usable size, no order is created.</p>
 */
public final class OrderRequestFactory {

    private OrderRequestFactory() {
    }

    /**
     * Builds a request for a qualified, assessed trade.
     *
     * @throws ExecutionValidationException when the assessment has no usable size
     */
    public static OrderRequest create(OrderIntent intent, RiskAssessment assessment) {
        Objects.requireNonNull(intent, "intent must not be null");
        Objects.requireNonNull(assessment, "assessment must not be null");
        if (!intent.strategyId().equals(assessment.strategyId())) {
            throw new ExecutionValidationException(
                    "strategyId of the intent must match the risk assessment");
        }
        if (!intent.instrument().equals(assessment.instrument())) {
            throw new ExecutionValidationException(
                    "instrument of the intent must match the risk assessment");
        }
        BigDecimal quantity = assessment.quantity();
        if (quantity == null || quantity.signum() <= 0) {
            throw new ExecutionValidationException(
                    "risk assessment does not provide a usable quantity");
        }
        BigDecimal reference = assessment.sizing().referencePrice();
        if (reference.signum() <= 0) {
            throw new ExecutionValidationException(
                    "risk assessment does not provide a usable reference price");
        }
        BigDecimal distance = assessment.stopDistance();
        Optional<BigDecimal> stop = distance.signum() > 0
                ? Optional.of(protectiveStop(intent.side(), reference, distance))
                : Optional.empty();
        return new OrderRequest(intent.strategyId(), intent.instrument(), intent.side(),
                intent.type(), quantity, intent.limitPrice(), intent.triggerPrice(), stop,
                intent.targetPrice(), intent.timeInForce(), assessment.timestamp());
    }

    /**
     * Stop price for a side: below the reference for a long, above it for a short.
     */
    public static BigDecimal protectiveStop(OrderSide side, BigDecimal reference,
                                            BigDecimal distance) {
        BigDecimal stop = side == OrderSide.BUY
                ? reference.subtract(distance)
                : reference.add(distance);
        return stop.signum() > 0 ? stop : BigDecimal.ZERO;
    }
}

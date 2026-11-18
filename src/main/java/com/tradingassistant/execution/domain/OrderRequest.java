package com.tradingassistant.execution.domain;

import com.tradingassistant.market.domain.InstrumentKey;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable description of an order the caller wants to place.
 *
 * <p>The request carries the direction explicitly. Earlier phases deliberately
 * describe the market without prescribing a side, so execution never infers one:
 * whoever decides to trade supplies it here.</p>
 *
 * @param strategyId    strategy the order belongs to
 * @param instrument    instrument to trade
 * @param side          buy or sell
 * @param type          order type
 * @param quantity      quantity to trade
 * @param limitPrice    limit price, for limit and stop-limit orders
 * @param triggerPrice  trigger price, for stop and stop-limit orders
 * @param stopPrice     protective stop referenced by the risk assessment
 * @param targetPrice   optional profit boundary
 * @param timeInForce   day, immediate-or-cancel or good-till-cancelled
 * @param timestamp     market instant the request belongs to
 */
public record OrderRequest(
        String strategyId,
        InstrumentKey instrument,
        OrderSide side,
        OrderType type,
        BigDecimal quantity,
        Optional<BigDecimal> limitPrice,
        Optional<BigDecimal> triggerPrice,
        Optional<BigDecimal> stopPrice,
        Optional<BigDecimal> targetPrice,
        TimeInForce timeInForce,
        Instant timestamp) {

    public OrderRequest {
        strategyId = ExecutionValue.requireText(strategyId, "strategyId");
        instrument = Objects.requireNonNull(instrument, "instrument must not be null");
        side = Objects.requireNonNull(side, "side must not be null");
        type = Objects.requireNonNull(type, "type must not be null");
        quantity = ExecutionValue.requirePositive(quantity, "quantity");
        limitPrice = positive(limitPrice, "limitPrice");
        triggerPrice = positive(triggerPrice, "triggerPrice");
        stopPrice = positive(stopPrice, "stopPrice");
        targetPrice = positive(targetPrice, "targetPrice");
        timeInForce = Objects.requireNonNull(timeInForce, "timeInForce must not be null");
        timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
    }

    /**
     * Quantity multiplied by the reference price, used for margin and leverage.
     */
    public BigDecimal notional(BigDecimal referencePrice) {
        BigDecimal price = referencePrice == null ? BigDecimal.ZERO : referencePrice;
        return quantity.multiply(price);
    }

    public boolean marketOrder() {
        return type == OrderType.MARKET;
    }

    private static Optional<BigDecimal> positive(Optional<BigDecimal> value, String field) {
        if (value == null || value.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(ExecutionValue.requirePositive(value.get(), field));
    }
}

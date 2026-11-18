package com.tradingassistant.execution.domain;

import com.tradingassistant.market.domain.InstrumentKey;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * What a caller wants executed, before the risk assessment supplies its size.
 *
 * <p>The intent fixes direction and order style; the quantity and protective stop
 * come from the risk assessment of the same strategy, instrument and instant. An
 * intent without a matching assessment is never turned into an order.</p>
 *
 * @param strategyId   strategy the intent belongs to
 * @param instrument   instrument to trade
 * @param side         buy or sell
 * @param type         order type
 * @param limitPrice   limit price, for limit and stop-limit orders
 * @param triggerPrice trigger price, for stop and stop-limit orders
 * @param targetPrice  optional profit boundary
 * @param timeInForce  day, immediate-or-cancel or good-till-cancelled
 */
public record OrderIntent(
        String strategyId,
        InstrumentKey instrument,
        OrderSide side,
        OrderType type,
        Optional<BigDecimal> limitPrice,
        Optional<BigDecimal> triggerPrice,
        Optional<BigDecimal> targetPrice,
        TimeInForce timeInForce) {

    public OrderIntent {
        strategyId = ExecutionValue.requireText(strategyId, "strategyId");
        instrument = Objects.requireNonNull(instrument, "instrument must not be null");
        side = Objects.requireNonNull(side, "side must not be null");
        type = Objects.requireNonNull(type, "type must not be null");
        limitPrice = positive(limitPrice, "limitPrice");
        triggerPrice = positive(triggerPrice, "triggerPrice");
        targetPrice = positive(targetPrice, "targetPrice");
        timeInForce = Objects.requireNonNull(timeInForce, "timeInForce must not be null");
    }

    public static OrderIntent market(String strategyId, InstrumentKey instrument,
                                     OrderSide side) {
        return new OrderIntent(strategyId, instrument, side, OrderType.MARKET,
                Optional.empty(), Optional.empty(), Optional.empty(), TimeInForce.DAY);
    }

    private static Optional<BigDecimal> positive(Optional<BigDecimal> value, String field) {
        if (value == null || value.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(ExecutionValue.requirePositive(value.get(), field));
    }
}

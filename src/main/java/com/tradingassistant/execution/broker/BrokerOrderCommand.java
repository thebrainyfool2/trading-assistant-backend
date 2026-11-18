package com.tradingassistant.execution.broker;

import com.tradingassistant.execution.domain.OrderSide;
import com.tradingassistant.execution.domain.OrderType;
import com.tradingassistant.execution.domain.TimeInForce;
import com.tradingassistant.market.domain.InstrumentKey;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable order instruction handed to a broker adapter.
 *
 * <p>It carries no strategy, risk or portfolio object: an adapter translates this
 * command into its own payload and translates the response back into an
 * {@code ExecutionReport}. That is the whole adapter contract.</p>
 *
 * @param orderId        application order identity
 * @param instrument     instrument to trade
 * @param side           buy or sell
 * @param type           order type
 * @param quantity       quantity to trade
 * @param limitPrice     limit price, when the type uses one
 * @param triggerPrice   trigger price, when the type uses one
 * @param timeInForce    day, immediate-or-cancel or good-till-cancelled
 * @param referencePrice price the adapter may use for a market fill
 */
public record BrokerOrderCommand(
        String orderId,
        InstrumentKey instrument,
        OrderSide side,
        OrderType type,
        BigDecimal quantity,
        Optional<BigDecimal> limitPrice,
        Optional<BigDecimal> triggerPrice,
        TimeInForce timeInForce,
        BigDecimal referencePrice) {

    public BrokerOrderCommand {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId must not be blank");
        }
        instrument = Objects.requireNonNull(instrument, "instrument must not be null");
        side = Objects.requireNonNull(side, "side must not be null");
        type = Objects.requireNonNull(type, "type must not be null");
        quantity = Objects.requireNonNull(quantity, "quantity must not be null");
        limitPrice = limitPrice == null ? Optional.empty() : limitPrice;
        triggerPrice = triggerPrice == null ? Optional.empty() : triggerPrice;
        timeInForce = Objects.requireNonNull(timeInForce, "timeInForce must not be null");
        referencePrice = Objects.requireNonNull(referencePrice, "referencePrice must not be null");
    }
}

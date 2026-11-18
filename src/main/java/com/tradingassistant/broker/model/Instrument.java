package com.tradingassistant.broker.model;

import java.math.BigDecimal;

/**
 * A single row from the public instrument master. Expiry remains the broker's
 * opaque string because formats vary by segment.
 */
public record Instrument(
        String symbolToken,
        String tradingSymbol,
        String name,
        String expiry,
        BigDecimal strike,
        BigDecimal tickSize,
        Long lotSize,
        String instrumentType,
        String exchangeSegment,
        BigDecimal freezeQuantity
) {
}

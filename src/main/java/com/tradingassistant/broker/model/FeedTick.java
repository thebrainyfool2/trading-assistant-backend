package com.tradingassistant.broker.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Normalized tick from SmartAPI Streaming 2.0. Prices are divided by 100 from
 * the protocol's paise representation. Fields not present in the selected mode
 * are null.
 */
public record FeedTick(
        FeedMode mode,
        Exchange exchange,
        String symbolToken,
        long sequenceNumber,
        Instant exchangeTimestamp,
        BigDecimal lastTradedPrice,
        Long lastTradedQuantity,
        BigDecimal averageTradedPrice,
        Long tradeVolume,
        Long totalBuyQuantity,
        Long totalSellQuantity,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        Instant lastTradedTimestamp,
        Long openInterest,
        MarketDepth depth,
        BigDecimal upperCircuit,
        BigDecimal lowerCircuit,
        BigDecimal week52High,
        BigDecimal week52Low
) {
}

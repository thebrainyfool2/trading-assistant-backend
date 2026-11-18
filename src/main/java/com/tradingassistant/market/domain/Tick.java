package com.tradingassistant.market.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One normalized live market update. The provider payload is not retained.
 * {@code exchangeTimestamp} is the provider's event time; {@code receivedAt} is
 * the application receipt time and is always present.
 */
public record Tick(
        TickMode mode,
        InstrumentKey instrument,
        long sequenceNumber,
        Instant exchangeTimestamp,
        Instant receivedAt,
        BigDecimal lastTradedPrice,
        Long lastTradedQuantity,
        BigDecimal averageTradedPrice,
        Long tradeVolume,
        Long totalBuyQuantity,
        Long totalSellQuantity,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal previousClose,
        Instant lastTradedTimestamp,
        Long openInterest,
        MarketDepth depth,
        BigDecimal upperCircuit,
        BigDecimal lowerCircuit,
        BigDecimal week52High,
        BigDecimal week52Low,
        MarketStatus marketStatus
) {

    public Tick {
        mode = DomainValidation.required(mode, "mode");
        instrument = DomainValidation.required(instrument, "instrument");
        receivedAt = DomainValidation.required(receivedAt, "receivedAt");
        lastTradedPrice = DomainValidation.nonNegative(lastTradedPrice, "lastTradedPrice");
        averageTradedPrice = DomainValidation.nonNegative(averageTradedPrice, "averageTradedPrice");
        lastTradedQuantity = DomainValidation.nonNegative(lastTradedQuantity, "lastTradedQuantity");
        tradeVolume = DomainValidation.nonNegative(tradeVolume, "tradeVolume");
        totalBuyQuantity = DomainValidation.nonNegative(totalBuyQuantity, "totalBuyQuantity");
        totalSellQuantity = DomainValidation.nonNegative(totalSellQuantity, "totalSellQuantity");
        open = DomainValidation.nonNegative(open, "open");
        high = DomainValidation.nonNegative(high, "high");
        low = DomainValidation.nonNegative(low, "low");
        previousClose = DomainValidation.nonNegative(previousClose, "previousClose");
        openInterest = DomainValidation.nonNegative(openInterest, "openInterest");
        upperCircuit = DomainValidation.nonNegative(upperCircuit, "upperCircuit");
        lowerCircuit = DomainValidation.nonNegative(lowerCircuit, "lowerCircuit");
        week52High = DomainValidation.nonNegative(week52High, "week52High");
        week52Low = DomainValidation.nonNegative(week52Low, "week52Low");
        depth = depth == null ? MarketDepth.empty() : depth;
        marketStatus = DomainValidation.required(marketStatus, "marketStatus");
        if (mode == TickMode.LTP && lastTradedPrice == null) {
            throw new IllegalArgumentException("LTP ticks require lastTradedPrice");
        }
        if (high != null && low != null && high.compareTo(low) < 0) {
            throw new IllegalArgumentException("high must not be below low");
        }
    }
}

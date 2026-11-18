package com.tradingassistant.market.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A point-in-time quote snapshot. Fields omitted by the requested quote mode
 * remain null; the timestamp is the application receipt time in UTC.
 */
public record Quote(
        InstrumentKey instrument,
        String tradingSymbol,
        BigDecimal lastTradedPrice,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal previousClose,
        BigDecimal averagePrice,
        Long volume,
        Long openInterest,
        Long lastTradeQuantity,
        BigDecimal netChange,
        BigDecimal percentChange,
        Long totalBuyQuantity,
        Long totalSellQuantity,
        Instant timestamp,
        MarketStatus marketStatus,
        BigDecimal upperCircuit,
        BigDecimal lowerCircuit,
        BigDecimal week52High,
        BigDecimal week52Low,
        MarketDepth depth
) {

    public Quote {
        instrument = DomainValidation.required(instrument, "instrument");
        tradingSymbol = DomainValidation.text(tradingSymbol, "tradingSymbol");
        lastTradedPrice = DomainValidation.nonNegative(lastTradedPrice, "lastTradedPrice");
        open = DomainValidation.nonNegative(open, "open");
        high = DomainValidation.nonNegative(high, "high");
        low = DomainValidation.nonNegative(low, "low");
        previousClose = DomainValidation.nonNegative(previousClose, "previousClose");
        averagePrice = DomainValidation.nonNegative(averagePrice, "averagePrice");
        volume = DomainValidation.nonNegative(volume, "volume");
        openInterest = DomainValidation.nonNegative(openInterest, "openInterest");
        lastTradeQuantity = DomainValidation.nonNegative(lastTradeQuantity, "lastTradeQuantity");
        totalBuyQuantity = DomainValidation.nonNegative(totalBuyQuantity, "totalBuyQuantity");
        totalSellQuantity = DomainValidation.nonNegative(totalSellQuantity, "totalSellQuantity");
        upperCircuit = DomainValidation.nonNegative(upperCircuit, "upperCircuit");
        lowerCircuit = DomainValidation.nonNegative(lowerCircuit, "lowerCircuit");
        week52High = DomainValidation.nonNegative(week52High, "week52High");
        week52Low = DomainValidation.nonNegative(week52Low, "week52Low");
        timestamp = DomainValidation.required(timestamp, "timestamp");
        marketStatus = DomainValidation.required(marketStatus, "marketStatus");
        depth = depth == null ? MarketDepth.empty() : depth;
        if (high != null && low != null && high.compareTo(low) < 0) {
            throw new IllegalArgumentException("high must not be below low");
        }
    }
}

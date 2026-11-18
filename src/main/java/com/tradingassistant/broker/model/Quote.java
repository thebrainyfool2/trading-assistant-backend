package com.tradingassistant.broker.model;

import java.math.BigDecimal;

/**
 * Broker-neutral quote snapshot. Fields not supplied by the selected quote mode
 * are null. Prices use the broker's normal rupee/currency unit, not paise.
 */
public record Quote(
        Exchange exchange,
        String symbolToken,
        String tradingSymbol,
        BigDecimal lastTradedPrice,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        Long lastTradeQuantity,
        BigDecimal netChange,
        BigDecimal percentChange,
        BigDecimal averagePrice,
        Long tradeVolume,
        Long openInterest,
        BigDecimal upperCircuit,
        BigDecimal lowerCircuit,
        BigDecimal week52High,
        BigDecimal week52Low,
        Long totalBuyQuantity,
        Long totalSellQuantity,
        MarketDepth depth
) {
}

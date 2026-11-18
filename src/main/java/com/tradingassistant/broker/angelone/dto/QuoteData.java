package com.tradingassistant.broker.angelone.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record QuoteData(
        String exchange,
        String tradingSymbol,
        String symbolToken,
        BigDecimal ltp,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        Long lastTradeQty,
        BigDecimal netChange,
        BigDecimal percentChange,
        BigDecimal avgPrice,
        Long tradeVolume,
        @JsonAlias({"openInterest", "opnInterest"}) Long openInterest,
        BigDecimal upperCircuit,
        BigDecimal lowerCircuit,
        @JsonProperty("52WeekHigh") BigDecimal week52High,
        @JsonProperty("52WeekLow") BigDecimal week52Low,
        @JsonAlias({"totBuyQuan"}) Long totalBuyQuantity,
        @JsonAlias({"totSellQuan"}) Long totalSellQuantity,
        DepthData depth
) {
}

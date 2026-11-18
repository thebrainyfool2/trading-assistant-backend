package com.tradingassistant.broker.angelone.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InstrumentDto(
        @JsonAlias({"exchange_token"}) String token,
        @JsonAlias({"tradingsymbol"}) String symbol,
        String name,
        String expiry,
        BigDecimal strike,
        @JsonAlias({"tick_size"}) BigDecimal tickSize,
        @JsonAlias({"lotsize", "lot_size"}) String lotSize,
        @JsonAlias({"instrumenttype", "instrument_type"}) String instrumentType,
        @JsonAlias({"exch_seg"}) String exchangeSegment,
        @JsonAlias({"freeze_qty"}) BigDecimal freezeQuantity
) {
}

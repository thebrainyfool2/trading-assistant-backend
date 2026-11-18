package com.tradingassistant.broker.angelone.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DepthData(List<Level> buy, List<Level> sell) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Level(BigDecimal price, Long quantity, Long orders) {
    }
}

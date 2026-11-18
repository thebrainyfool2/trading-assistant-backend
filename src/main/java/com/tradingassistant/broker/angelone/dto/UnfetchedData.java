package com.tradingassistant.broker.angelone.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UnfetchedData(String exchange, String symbolToken, String message) {
}

package com.tradingassistant.broker.angelone.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AngelOneEnvelope<T>(
        boolean status,
        String message,
        String errorcode,
        T data
) {
}

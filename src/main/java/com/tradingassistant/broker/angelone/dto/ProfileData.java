package com.tradingassistant.broker.angelone.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProfileData(
        String clientcode,
        String name,
        String email,
        String mobileno,
        JsonNode exchanges,
        JsonNode products,
        String lastlogintime,
        String brokerid
) {
}

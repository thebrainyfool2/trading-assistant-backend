package com.tradingassistant.broker.angelone.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record QuoteResponseData(List<QuoteData> fetched, List<UnfetchedData> unfetched) {
}

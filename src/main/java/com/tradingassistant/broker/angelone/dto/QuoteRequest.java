package com.tradingassistant.broker.angelone.dto;

import java.util.List;
import java.util.Map;

public record QuoteRequest(String mode, Map<String, List<String>> exchangeTokens) {
}

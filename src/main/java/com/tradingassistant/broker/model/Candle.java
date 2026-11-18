package com.tradingassistant.broker.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record Candle(
        OffsetDateTime timestamp,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume
) {
}

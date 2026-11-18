package com.tradingassistant.broker.model;

import java.math.BigDecimal;

public record DepthLevel(BigDecimal price, Long quantity, Long orders) {
}

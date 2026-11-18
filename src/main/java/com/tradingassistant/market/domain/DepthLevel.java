package com.tradingassistant.market.domain;

import java.math.BigDecimal;

/**
 * One side of a market-depth ladder. Orders and quantity are optional because
 * some feed modes omit them; price is always required for a usable level.
 */
public record DepthLevel(BigDecimal price, Long quantity, Long orders) {

    public DepthLevel {
        price = DomainValidation.required(price, "price");
        DomainValidation.nonNegative(price, "price");
        DomainValidation.nonNegative(quantity, "quantity");
        DomainValidation.nonNegative(orders, "orders");
    }
}

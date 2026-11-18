package com.tradingassistant.execution.domain;

import com.tradingassistant.execution.config.ExecutionConfig;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Everything an execution rule may read.
 *
 * @param request          order, risk assessment and market assessment
 * @param portfolio        portfolio snapshot at evaluation time
 * @param config           immutable execution policy
 * @param duplicateOrder   whether an active order already exists for the same key
 * @param brokerConnected  whether the selected adapter reports a connection
 * @param accountReady     whether the portfolio has usable capital
 * @param referencePrice   price used for notional, margin and stop checks
 */
public record ExecutionRuleContext(
        ExecutionRequest request,
        PortfolioSnapshot portfolio,
        ExecutionConfig config,
        boolean duplicateOrder,
        boolean brokerConnected,
        boolean accountReady,
        BigDecimal referencePrice) {

    public ExecutionRuleContext {
        request = Objects.requireNonNull(request, "request must not be null");
        portfolio = Objects.requireNonNull(portfolio, "portfolio must not be null");
        config = Objects.requireNonNull(config, "config must not be null");
        referencePrice = Objects.requireNonNull(referencePrice, "referencePrice must not be null");
    }

    public OrderRequest order() {
        return request.order();
    }

    public BigDecimal notional() {
        return order().notional(referencePrice);
    }

    public BigDecimal requiredMargin() {
        return config.marginFor(notional());
    }
}

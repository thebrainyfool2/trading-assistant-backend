package com.tradingassistant.execution.registry;

import com.tradingassistant.execution.api.ExecutionRule;
import com.tradingassistant.execution.rule.ExecutionRules;

import java.util.List;

/**
 * Deterministic catalog of the built-in execution rules, in evaluation order.
 */
public final class BuiltInExecutionRules {

    public static final String VALID_QUANTITY = "VALID_QUANTITY";
    public static final String VALID_PRICE = "VALID_PRICE";
    public static final String VALID_STOP = "VALID_STOP";
    public static final String VALID_TARGET = "VALID_TARGET";
    public static final String MARKET_OPEN = "MARKET_OPEN";
    public static final String BROKER_CONNECTED = "BROKER_CONNECTED";
    public static final String ACCOUNT_READY = "ACCOUNT_READY";
    public static final String SUFFICIENT_MARGIN = "SUFFICIENT_MARGIN";
    public static final String ORDER_NOT_DUPLICATE = "ORDER_NOT_DUPLICATE";
    public static final String POSITION_LIMIT = "POSITION_LIMIT";
    public static final String LEVERAGE_LIMIT = "LEVERAGE_LIMIT";
    public static final String VALID_PRODUCT = "VALID_PRODUCT";
    public static final String VALID_ORDER_TYPE = "VALID_ORDER_TYPE";

    private BuiltInExecutionRules() {
    }

    public static List<ExecutionRule> all() {
        return List.of(
                new ExecutionRules.ValidQuantity(VALID_QUANTITY),
                new ExecutionRules.ValidPrice(VALID_PRICE),
                new ExecutionRules.ValidStop(VALID_STOP),
                new ExecutionRules.ValidTarget(VALID_TARGET),
                new ExecutionRules.MarketOpen(MARKET_OPEN),
                new ExecutionRules.BrokerConnected(BROKER_CONNECTED),
                new ExecutionRules.AccountReady(ACCOUNT_READY),
                new ExecutionRules.SufficientMargin(SUFFICIENT_MARGIN),
                new ExecutionRules.OrderNotDuplicate(ORDER_NOT_DUPLICATE),
                new ExecutionRules.PositionLimit(POSITION_LIMIT),
                new ExecutionRules.LeverageLimit(LEVERAGE_LIMIT),
                new ExecutionRules.ValidProduct(VALID_PRODUCT),
                new ExecutionRules.ValidOrderType(VALID_ORDER_TYPE));
    }
}

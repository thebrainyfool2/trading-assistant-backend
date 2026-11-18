package com.tradingassistant.execution.api;

import com.tradingassistant.execution.domain.ExecutionRuleContext;
import com.tradingassistant.execution.domain.ExecutionRuleEvaluation;

/**
 * One independently evaluable order precondition.
 *
 * <p>Rules are immutable, receive an immutable context and return one outcome.
 * They never call a broker, never mutate the portfolio and never decide whether
 * the trade is desirable.</p>
 */
public interface ExecutionRule {

    String id();

    ExecutionRuleEvaluation evaluate(ExecutionRuleContext context);
}

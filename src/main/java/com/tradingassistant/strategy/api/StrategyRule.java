package com.tradingassistant.strategy.api;

import com.tradingassistant.strategy.domain.RuleEvaluation;
import com.tradingassistant.strategy.domain.StrategyEvaluationContext;

/**
 * One deterministic condition of a strategy.
 *
 * <p>A rule sees only the immutable evaluation context and its own configuration.
 * It never reads another rule, the registry, an engine or any state, so rules can
 * be evaluated independently, in any order and in parallel.</p>
 */
public interface StrategyRule {

    /**
     * Stable identity of the rule inside its strategy.
     */
    String id();

    /**
     * Evaluates the condition against the supplied context.
     */
    RuleEvaluation evaluate(StrategyEvaluationContext context);
}

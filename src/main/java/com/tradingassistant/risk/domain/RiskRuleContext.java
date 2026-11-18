package com.tradingassistant.risk.domain;

import com.tradingassistant.risk.config.RiskEngineConfig;

import java.util.Objects;

/**
 * What a risk rule is allowed to read: the immutable inputs, the derived plan and
 * the configuration.
 *
 * <p>Passing the derived plan keeps every rule on the same numbers. A rule never
 * recomputes a stop or a size and never mutates anything, so rules stay
 * independent, order-free and safe to evaluate in parallel.</p>
 *
 * @param context immutable market and qualification inputs
 * @param plan    risk mathematics derived once by the calculator
 * @param config  immutable risk policy
 */
public record RiskRuleContext(
        RiskContext context,
        RiskPlan plan,
        RiskEngineConfig config) {

    public RiskRuleContext {
        context = Objects.requireNonNull(context, "context must not be null");
        plan = Objects.requireNonNull(plan, "plan must not be null");
        config = Objects.requireNonNull(config, "config must not be null");
    }
}

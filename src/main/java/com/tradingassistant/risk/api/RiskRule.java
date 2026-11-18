package com.tradingassistant.risk.api;

import com.tradingassistant.risk.domain.RiskRuleContext;
import com.tradingassistant.risk.domain.RiskRuleEvaluation;

/**
 * One independently evaluable risk-policy condition.
 *
 * <p>A rule receives the immutable inputs, the derived risk plan and the
 * configuration, and returns one outcome. Rules never see the registry, the
 * engine or each other, hold no state and are evaluated independently, so the same
 * instance serves every assessment.</p>
 *
 * <p>A rule checks a boundary — a stop distance, a leverage ceiling, a loss cap,
 * a reward-to-risk floor. It never decides whether a trade should be taken, never
 * recommends a direction and never places anything.</p>
 */
public interface RiskRule {

    /**
     * Stable identity used by registries, reports and tests.
     */
    String id();

    /**
     * Evaluates the boundary against one immutable rule context.
     *
     * @return an immutable outcome; never null
     */
    RiskRuleEvaluation evaluate(RiskRuleContext context);
}

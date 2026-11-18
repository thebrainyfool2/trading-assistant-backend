package com.tradingassistant.qualification.api;

import com.tradingassistant.qualification.domain.QualificationRuleEvaluation;
import com.tradingassistant.qualification.domain.TradeQualificationContext;

/**
 * One independently evaluable qualification condition.
 *
 * <p>A rule receives the immutable context and its own configuration only: no
 * registry, no engine, no other rule and no cached state. Implementations must be
 * immutable and thread-safe so the same instance can serve every qualification
 * without coordinating with anything else.</p>
 *
 * <p>A rule answers a narrow question such as "is the session open?" or "does the
 * market assessment meet the configured confidence floor?". It never decides
 * whether a trade should be taken, never scores, ranks or sizes anything.</p>
 */
public interface QualificationRule {

    /**
     * Stable identity used by registries, reports and tests.
     */
    String id();

    /**
     * Evaluates the condition against one immutable context.
     *
     * @return an immutable outcome; never null
     */
    QualificationRuleEvaluation evaluate(TradeQualificationContext context);
}

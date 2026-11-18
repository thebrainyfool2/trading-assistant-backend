package com.tradingassistant.risk.domain;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable outcome of one risk rule.
 *
 * <p>{@code passed()} is derived from {@code status}. Metadata is descriptive
 * only: the observed value and the configured boundary that was compared, never a
 * score, a probability or a recommendation.</p>
 *
 * @param ruleId   identity of the rule that produced this evaluation
 * @param status   outcome of the boundary check
 * @param reason   short deterministic explanation
 * @param metadata observed values and limits used by the check
 */
public record RiskRuleEvaluation(
        String ruleId,
        RiskRuleStatus status,
        String reason,
        Map<String, String> metadata) {

    public RiskRuleEvaluation {
        if (ruleId == null || ruleId.isBlank()) {
            throw new IllegalArgumentException("ruleId must not be blank");
        }
        status = Objects.requireNonNull(status, "status must not be null");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        ruleId = ruleId.trim();
        reason = reason.trim();
    }

    public boolean passed() {
        return status == RiskRuleStatus.PASSED;
    }

    public static RiskRuleEvaluation passed(String ruleId, String reason,
                                            Map<String, String> metadata) {
        return new RiskRuleEvaluation(ruleId, RiskRuleStatus.PASSED, reason, metadata);
    }

    public static RiskRuleEvaluation failed(String ruleId, String reason,
                                            Map<String, String> metadata) {
        return new RiskRuleEvaluation(ruleId, RiskRuleStatus.FAILED, reason, metadata);
    }

    public static RiskRuleEvaluation insufficient(String ruleId, String reason) {
        return new RiskRuleEvaluation(ruleId, RiskRuleStatus.INSUFFICIENT_DATA, reason, Map.of());
    }

    public static RiskRuleEvaluation unknown(String ruleId, String reason) {
        return new RiskRuleEvaluation(ruleId, RiskRuleStatus.UNKNOWN, reason, Map.of());
    }
}

package com.tradingassistant.execution.domain;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable outcome of one execution rule.
 *
 * @param ruleId   identity of the rule
 * @param status   outcome of the check
 * @param reason   short deterministic explanation
 * @param metadata observed values and limits used by the check
 */
public record ExecutionRuleEvaluation(
        String ruleId,
        ExecutionRuleStatus status,
        String reason,
        Map<String, String> metadata) {

    public ExecutionRuleEvaluation {
        ruleId = ExecutionValue.requireText(ruleId, "ruleId");
        status = Objects.requireNonNull(status, "status must not be null");
        reason = ExecutionValue.requireText(reason, "reason");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public boolean passed() {
        return status == ExecutionRuleStatus.PASSED;
    }

    public static ExecutionRuleEvaluation passed(String ruleId, String reason,
                                                 Map<String, String> metadata) {
        return new ExecutionRuleEvaluation(ruleId, ExecutionRuleStatus.PASSED, reason, metadata);
    }

    public static ExecutionRuleEvaluation failed(String ruleId, String reason,
                                                 Map<String, String> metadata) {
        return new ExecutionRuleEvaluation(ruleId, ExecutionRuleStatus.FAILED, reason, metadata);
    }

    public static ExecutionRuleEvaluation insufficient(String ruleId, String reason) {
        return new ExecutionRuleEvaluation(ruleId, ExecutionRuleStatus.INSUFFICIENT_DATA,
                reason, Map.of());
    }

    public static ExecutionRuleEvaluation unknown(String ruleId, String reason) {
        return new ExecutionRuleEvaluation(ruleId, ExecutionRuleStatus.UNKNOWN, reason, Map.of());
    }
}

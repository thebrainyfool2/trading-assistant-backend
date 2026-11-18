package com.tradingassistant.strategy.domain;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable outcome of one rule evaluation.
 *
 * <p>{@code passed()} is derived from {@code status}, so a rule can never report
 * a pass and a non-passing status at the same time. Metadata is descriptive
 * only: observed values and the threshold that was compared, never a score, a
 * probability or a recommendation.</p>
 *
 * @param ruleId   identity of the rule that produced this evaluation
 * @param status   outcome of the condition
 * @param reason   short deterministic explanation
 * @param metadata observed values and thresholds used by the condition
 */
public record RuleEvaluation(
        String ruleId,
        EvaluationStatus status,
        String reason,
        Map<String, String> metadata) {

    public RuleEvaluation {
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
        return status == EvaluationStatus.PASSED;
    }

    public static RuleEvaluation passed(String ruleId, String reason,
                                        Map<String, String> metadata) {
        return new RuleEvaluation(ruleId, EvaluationStatus.PASSED, reason, metadata);
    }

    public static RuleEvaluation failed(String ruleId, String reason,
                                        Map<String, String> metadata) {
        return new RuleEvaluation(ruleId, EvaluationStatus.FAILED, reason, metadata);
    }

    public static RuleEvaluation insufficient(String ruleId, String reason) {
        return new RuleEvaluation(ruleId, EvaluationStatus.INSUFFICIENT_DATA, reason, Map.of());
    }

    public static RuleEvaluation unknown(String ruleId, String reason) {
        return new RuleEvaluation(ruleId, EvaluationStatus.UNKNOWN, reason, Map.of());
    }
}

package com.tradingassistant.qualification.domain;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable outcome of one qualification rule.
 *
 * <p>{@code passed()} is derived from {@code status}, so a rule can never report a
 * pass and a non-passing status at the same time. Metadata is descriptive only:
 * observed values and the threshold that was compared, never a score, a
 * probability or a recommendation.</p>
 *
 * @param ruleId   identity of the rule that produced this evaluation
 * @param status   outcome of the condition
 * @param reason   short deterministic explanation
 * @param metadata observed values and thresholds used by the condition
 */
public record QualificationRuleEvaluation(
        String ruleId,
        RuleStatus status,
        String reason,
        Map<String, String> metadata) {

    public QualificationRuleEvaluation {
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
        return status == RuleStatus.PASSED;
    }

    public static QualificationRuleEvaluation passed(String ruleId, String reason,
                                                     Map<String, String> metadata) {
        return new QualificationRuleEvaluation(ruleId, RuleStatus.PASSED, reason, metadata);
    }

    public static QualificationRuleEvaluation failed(String ruleId, String reason,
                                                     Map<String, String> metadata) {
        return new QualificationRuleEvaluation(ruleId, RuleStatus.FAILED, reason, metadata);
    }

    public static QualificationRuleEvaluation insufficient(String ruleId, String reason) {
        return new QualificationRuleEvaluation(ruleId, RuleStatus.INSUFFICIENT_DATA, reason,
                Map.of());
    }

    public static QualificationRuleEvaluation unknown(String ruleId, String reason) {
        return new QualificationRuleEvaluation(ruleId, RuleStatus.UNKNOWN, reason, Map.of());
    }
}

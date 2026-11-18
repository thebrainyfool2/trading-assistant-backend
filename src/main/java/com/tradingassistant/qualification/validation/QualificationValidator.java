package com.tradingassistant.qualification.validation;

import com.tradingassistant.qualification.api.QualificationRule;
import com.tradingassistant.qualification.config.TradeQualificationConfig;
import com.tradingassistant.qualification.domain.TradeQualificationContext;
import com.tradingassistant.qualification.exception.QualificationValidationException;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Deterministic validation of qualification inputs, rule batches and limits.
 *
 * <p>Model constructors reject structurally invalid values; this class adds the
 * checks that depend on configuration and the cross-rule checks a single rule
 * cannot perform. Every message names the offending field or identifier.</p>
 */
public final class QualificationValidator {

    private QualificationValidator() {
    }

    /**
     * Rejects a null context and any context whose parts disagree. The context
     * record performs the detailed field checks; this method is the boundary a
     * service calls before touching an event payload.
     */
    public static void validateContext(TradeQualificationContext context) {
        if (context == null) {
            throw new QualificationValidationException("context must not be null");
        }
    }

    /**
     * Validates a rule batch: non-null, no null entries, no duplicate ids and
     * within the configured size.
     */
    public static void validateRules(List<QualificationRule> rules,
                                     TradeQualificationConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        if (rules == null) {
            throw new QualificationValidationException("rules must not be null");
        }
        if (rules.isEmpty()) {
            throw new QualificationValidationException("rules must not be empty");
        }
        if (rules.size() > config.maxRules()) {
            throw new QualificationValidationException(
                    "rule batch exceeds maxRules " + config.maxRules());
        }
        Set<String> ids = new HashSet<>();
        for (QualificationRule rule : rules) {
            if (rule == null) {
                throw new QualificationValidationException(
                        "rules must not contain null entries");
            }
            String id = rule.id();
            if (id == null || id.isBlank()) {
                throw new QualificationValidationException("rule id must not be blank");
            }
            if (!ids.add(id)) {
                throw new QualificationValidationException("duplicate rule id " + id);
            }
        }
    }
}

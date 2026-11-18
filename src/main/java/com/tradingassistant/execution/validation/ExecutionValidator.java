package com.tradingassistant.execution.validation;

import com.tradingassistant.execution.api.ExecutionRule;
import com.tradingassistant.execution.broker.BrokerAdapter;
import com.tradingassistant.execution.broker.PaperBrokerAdapter;
import com.tradingassistant.execution.config.ExecutionConfig;
import com.tradingassistant.execution.config.ExecutionMode;
import com.tradingassistant.execution.domain.ExecutionRequest;
import com.tradingassistant.execution.exception.ExecutionValidationException;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Deterministic validation of execution inputs, rule batches and policy.
 *
 * <p>Model constructors reject structurally invalid values and
 * {@link com.tradingassistant.execution.config.ExecutionConfig} rejects an unusable
 * policy. This class adds the checks performed at the engine boundary: null
 * requests, null order identifiers, duplicate rules and an inconsistent mode.</p>
 */
public final class ExecutionValidator {

    private ExecutionValidator() {
    }

    public static void validateRequest(ExecutionRequest request) {
        if (request == null) {
            throw new ExecutionValidationException("request must not be null");
        }
    }

    public static void validateOrderId(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            throw new ExecutionValidationException("orderId must not be blank");
        }
    }

    public static void validateRules(List<ExecutionRule> rules) {
        if (rules == null) {
            throw new ExecutionValidationException("rules must not be null");
        }
        if (rules.isEmpty()) {
            throw new ExecutionValidationException("rules must not be empty");
        }
        Set<String> ids = new HashSet<>();
        for (ExecutionRule rule : rules) {
            if (rule == null) {
                throw new ExecutionValidationException(
                        "rules must not contain null entries");
            }
            String id = rule.id();
            if (id == null || id.isBlank()) {
                throw new ExecutionValidationException("rule id must not be blank");
            }
            if (!ids.add(id)) {
                throw new ExecutionValidationException("duplicate rule id " + id);
            }
        }
    }

    /**
     * Rejects a live configuration that is wired to the paper adapter, so a live
     * deployment can never silently simulate fills.
     */
    public static void validateMode(ExecutionConfig config, BrokerAdapter adapter) {
        Objects.requireNonNull(config, "config must not be null");
        if (config.mode() == ExecutionMode.LIVE
                && PaperBrokerAdapter.NAME.equals(adapter.brokerName())) {
            throw new ExecutionValidationException(
                    "LIVE mode requires a live broker adapter, not the paper adapter");
        }
    }
}

package com.tradingassistant.execution.domain;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Descriptive diagnostics for one execution attempt.
 *
 * @param values   named calculation inputs and intermediate values
 * @param warnings non-fatal observations, in deterministic order
 * @param attempts broker submission attempts that were made
 */
public record ExecutionDiagnostics(
        Map<String, String> values,
        List<String> warnings,
        int attempts) {

    public ExecutionDiagnostics {
        values = values == null ? Map.of() : Map.copyOf(values);
        if (warnings == null || warnings.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("warnings must not contain null entries");
        }
        warnings = List.copyOf(warnings);
        if (attempts < 0) {
            throw new IllegalArgumentException("attempts must not be negative");
        }
    }

    public static ExecutionDiagnostics empty() {
        return new ExecutionDiagnostics(Map.of(), List.of(), 0);
    }
}

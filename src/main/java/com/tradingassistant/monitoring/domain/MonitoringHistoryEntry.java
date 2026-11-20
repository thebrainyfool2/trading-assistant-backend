package com.tradingassistant.monitoring.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Persisted record of one significant monitoring transition. */
public record MonitoringHistoryEntry(
        String tradeId,
        Instant timestamp,
        Optional<MonitoringState> previousState,
        MonitoringState currentState,
        Optional<ExitRecommendation> previousExitRecommendation,
        ExitRecommendation currentExitRecommendation,
        BigDecimal previousConfidence,
        BigDecimal currentConfidence,
        BigDecimal previousDirectionalEdge,
        BigDecimal currentDirectionalEdge,
        String explanation,
        List<MonitoringWarning> warnings) {

    public MonitoringHistoryEntry {
        if (tradeId == null || tradeId.isBlank()) {
            throw new IllegalArgumentException("tradeId must not be blank");
        }
        tradeId = tradeId.trim();
        timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
        previousState = previousState == null ? Optional.empty() : previousState;
        currentState = Objects.requireNonNull(currentState, "currentState must not be null");
        previousExitRecommendation = previousExitRecommendation == null
                ? Optional.empty() : previousExitRecommendation;
        currentExitRecommendation = Objects.requireNonNull(currentExitRecommendation,
                "currentExitRecommendation must not be null");
        previousConfidence = Objects.requireNonNull(previousConfidence,
                "previousConfidence must not be null");
        currentConfidence = Objects.requireNonNull(currentConfidence,
                "currentConfidence must not be null");
        previousDirectionalEdge = Objects.requireNonNull(previousDirectionalEdge,
                "previousDirectionalEdge must not be null");
        currentDirectionalEdge = Objects.requireNonNull(currentDirectionalEdge,
                "currentDirectionalEdge must not be null");
        if (explanation == null || explanation.isBlank()) {
            throw new IllegalArgumentException("explanation must not be blank");
        }
        explanation = explanation.trim();
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
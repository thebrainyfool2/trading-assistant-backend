package com.tradingassistant.monitoring.domain;

import com.tradingassistant.recommendation.domain.ScorerId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable, fully explainable result of rescoring one active manual trade.
 *
 * <p>{@code currentDirectionalEdge} is aligned to the trade's direction: a
 * positive value supports the trade, whether it is a call or a put. The signed
 * Phase 14 edge remains available in the recommendation.</p>
 */
public record TradeMonitoringResult(
        String tradeId,
        Instant timestamp,
        MonitoringState monitoringState,
        ExitRecommendation exitRecommendation,
        BigDecimal currentConfidence,
        BigDecimal currentDirectionalEdge,
        BigDecimal confidenceDelta,
        BigDecimal directionalEdgeDelta,
        List<ComponentChange> componentChanges,
        Map<ScorerId, BigDecimal> componentScores,
        List<MonitoringWarning> warnings,
        String explanation,
        Map<String, String> diagnostics) {

    public TradeMonitoringResult {
        if (tradeId == null || tradeId.isBlank()) {
            throw new IllegalArgumentException("tradeId must not be blank");
        }
        tradeId = tradeId.trim();
        timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
        monitoringState = Objects.requireNonNull(monitoringState,
                "monitoringState must not be null");
        exitRecommendation = Objects.requireNonNull(exitRecommendation,
                "exitRecommendation must not be null");
        currentConfidence = Objects.requireNonNull(currentConfidence,
                "currentConfidence must not be null");
        currentDirectionalEdge = Objects.requireNonNull(currentDirectionalEdge,
                "currentDirectionalEdge must not be null");
        confidenceDelta = Objects.requireNonNull(confidenceDelta,
                "confidenceDelta must not be null");
        directionalEdgeDelta = Objects.requireNonNull(directionalEdgeDelta,
                "directionalEdgeDelta must not be null");
        if (currentConfidence.signum() < 0
                || currentConfidence.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("currentConfidence must be between 0 and 100");
        }
        componentChanges = componentChanges == null ? List.of() : List.copyOf(componentChanges);
        Set<ScorerId> seen = new HashSet<>();
        for (ComponentChange change : componentChanges) {
            if (!seen.add(change.scorerId())) {
                throw new IllegalArgumentException("duplicate component change: "
                        + change.scorerId());
            }
        }
        componentScores = componentScores == null ? Map.of() : Map.copyOf(componentScores);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        if (explanation == null || explanation.isBlank()) {
            throw new IllegalArgumentException("explanation must not be blank");
        }
        explanation = explanation.trim();
        diagnostics = diagnostics == null ? Map.of() : Map.copyOf(diagnostics);
    }

    public boolean exitNow() {
        return exitRecommendation == ExitRecommendation.EXIT_NOW;
    }
}
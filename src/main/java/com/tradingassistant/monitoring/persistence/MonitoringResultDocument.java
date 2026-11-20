package com.tradingassistant.monitoring.persistence;

import com.tradingassistant.monitoring.domain.ComponentChange;
import com.tradingassistant.monitoring.domain.ExitRecommendation;
import com.tradingassistant.monitoring.domain.MonitoringState;
import com.tradingassistant.monitoring.domain.MonitoringWarning;
import com.tradingassistant.monitoring.domain.TradeMonitoringResult;
import com.tradingassistant.recommendation.domain.ScorerId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Latest monitoring result for one manual trade. */
@Document(collection = "monitoring_results")
public record MonitoringResultDocument(
        @Id String tradeId,
        Instant timestamp,
        MonitoringState monitoringState,
        ExitRecommendation exitRecommendation,
        BigDecimal currentConfidence,
        BigDecimal currentDirectionalEdge,
        BigDecimal confidenceDelta,
        BigDecimal directionalEdgeDelta,
        List<ComponentChangeDocument> componentChanges,
        Map<String, BigDecimal> componentScores,
        List<MonitoringWarningDocument> warnings,
        String explanation,
        Map<String, String> diagnostics) {

    public static MonitoringResultDocument from(TradeMonitoringResult result) {
        Map<String, BigDecimal> scores = new java.util.LinkedHashMap<>();
        result.componentScores().forEach((key, value) -> scores.put(key.name(), value));
        return new MonitoringResultDocument(result.tradeId(), result.timestamp(),
                result.monitoringState(), result.exitRecommendation(),
                result.currentConfidence(), result.currentDirectionalEdge(),
                result.confidenceDelta(), result.directionalEdgeDelta(),
                result.componentChanges().stream().map(ComponentChangeDocument::from)
                        .toList(), scores,
                result.warnings().stream().map(MonitoringWarningDocument::from).toList(),
                result.explanation(), result.diagnostics());
    }

    public TradeMonitoringResult toDomain() {
        Map<ScorerId, BigDecimal> scores = new EnumMap<>(ScorerId.class);
        if (componentScores != null) {
            componentScores.forEach((key, value) ->
                    scores.put(ScorerId.valueOf(key), value));
        }
        List<ComponentChange> changes = componentChanges == null ? List.of()
                : componentChanges.stream().map(ComponentChangeDocument::toDomain)
                .toList();
        List<MonitoringWarning> warningValues = warnings == null ? List.of()
                : warnings.stream().map(MonitoringWarningDocument::toDomain).toList();
        return new TradeMonitoringResult(tradeId, timestamp, monitoringState,
                exitRecommendation, currentConfidence, currentDirectionalEdge,
                confidenceDelta, directionalEdgeDelta, changes, scores, warningValues,
                explanation, diagnostics);
    }
}
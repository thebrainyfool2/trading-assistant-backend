package com.tradingassistant.monitoring.persistence;

import com.tradingassistant.monitoring.domain.ExitRecommendation;
import com.tradingassistant.monitoring.domain.MonitoringHistoryEntry;
import com.tradingassistant.monitoring.domain.MonitoringState;
import com.tradingassistant.monitoring.domain.MonitoringWarning;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Persisted significant monitoring transition for replay and validation. */
@Document(collection = "monitoring_history")
@CompoundIndex(name = "monitoring_history_trade_time",
        def = "{'tradeId': 1, 'timestamp': -1}")
public record MonitoringHistoryDocument(
        @Id String id,
        String tradeId,
        Instant timestamp,
        MonitoringState previousState,
        MonitoringState currentState,
        ExitRecommendation previousExitRecommendation,
        ExitRecommendation currentExitRecommendation,
        BigDecimal previousConfidence,
        BigDecimal currentConfidence,
        BigDecimal previousDirectionalEdge,
        BigDecimal currentDirectionalEdge,
        String explanation,
        List<MonitoringWarningDocument> warnings) {

    public static MonitoringHistoryDocument from(MonitoringHistoryEntry entry) {
        return new MonitoringHistoryDocument(null, entry.tradeId(), entry.timestamp(),
                entry.previousState().orElse(null), entry.currentState(),
                entry.previousExitRecommendation().orElse(null),
                entry.currentExitRecommendation(), entry.previousConfidence(),
                entry.currentConfidence(), entry.previousDirectionalEdge(),
                entry.currentDirectionalEdge(), entry.explanation(),
                entry.warnings().stream().map(MonitoringWarningDocument::from).toList());
    }

    public MonitoringHistoryEntry toDomain() {
        List<MonitoringWarning> values = warnings == null ? List.of()
                : warnings.stream().map(MonitoringWarningDocument::toDomain).toList();
        return new MonitoringHistoryEntry(tradeId, timestamp,
                Optional.ofNullable(previousState), currentState,
                Optional.ofNullable(previousExitRecommendation),
                currentExitRecommendation, previousConfidence, currentConfidence,
                previousDirectionalEdge, currentDirectionalEdge, explanation, values);
    }
}
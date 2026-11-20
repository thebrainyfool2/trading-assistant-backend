package com.tradingassistant.monitoring.api;

import com.tradingassistant.monitoring.domain.ActiveTradeMonitoring;
import com.tradingassistant.monitoring.domain.ComponentChange;
import com.tradingassistant.monitoring.domain.ManualTrade;
import com.tradingassistant.monitoring.domain.MonitoringAlert;
import com.tradingassistant.monitoring.domain.MonitoringHistoryEntry;
import com.tradingassistant.monitoring.domain.MonitoringWarning;
import com.tradingassistant.monitoring.domain.TradeMonitoringResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Stable read-only API projections for monitoring. */
public final class MonitoringDtos {

    private MonitoringDtos() {
    }

    public record TradeView(
            String tradeId,
            String instrument,
            String interval,
            String recommendationType,
            String optionSymbol,
            BigDecimal strike,
            LocalDate expiry,
            Instant entryTimestamp,
            BigDecimal entryPrice,
            BigDecimal quantity,
            String notes,
            String status,
            Optional<BigDecimal> targetPremium,
            Optional<BigDecimal> stopPremium,
            Optional<Instant> recommendationTimestamp) {

        public static TradeView from(ManualTrade trade) {
            return new TradeView(trade.tradeId(), trade.instrument().symbolToken(),
                    trade.interval().name(), trade.recommendationType().name(),
                    trade.optionSymbol(), trade.strike(), trade.expiry(),
                    trade.entryTimestamp(), trade.entryPrice(), trade.quantity(),
                    trade.notes(), trade.status().name(), trade.targetPremium(),
                    trade.stopPremium(), trade.recommendationTimestamp());
        }
    }

    public record ResultView(
            String tradeId,
            Instant timestamp,
            String monitoringState,
            String exitRecommendation,
            BigDecimal currentConfidence,
            BigDecimal currentDirectionalEdge,
            BigDecimal confidenceDelta,
            BigDecimal directionalEdgeDelta,
            List<ComponentChangeView> componentChanges,
            Map<String, BigDecimal> componentScores,
            List<WarningView> warnings,
            String explanation,
            Map<String, String> diagnostics) {

        public static ResultView from(TradeMonitoringResult result) {
            Map<String, BigDecimal> scores = new java.util.LinkedHashMap<>();
            result.componentScores().forEach((key, value) -> scores.put(key.id(), value));
            return new ResultView(result.tradeId(), result.timestamp(),
                    result.monitoringState().name(), result.exitRecommendation().name(),
                    result.currentConfidence(), result.currentDirectionalEdge(),
                    result.confidenceDelta(), result.directionalEdgeDelta(),
                    result.componentChanges().stream().map(ComponentChangeView::from)
                            .toList(), scores,
                    result.warnings().stream().map(WarningView::from).toList(),
                    result.explanation(), result.diagnostics());
        }
    }

    public record ComponentChangeView(
            String scorerId,
            BigDecimal previousScore,
            BigDecimal currentScore,
            BigDecimal delta,
            String explanation) {

        public static ComponentChangeView from(ComponentChange change) {
            return new ComponentChangeView(change.scorerId().id(),
                    change.previousScore(), change.currentScore(), change.delta(),
                    change.explanation());
        }
    }

    public record WarningView(
            String type,
            String message,
            Map<String, String> diagnostics) {

        public static WarningView from(MonitoringWarning warning) {
            return new WarningView(warning.type().name(), warning.message(),
                    warning.diagnostics());
        }
    }

    public record ActiveView(
            TradeView trade,
            Optional<ResultView> latestResult) {

        public static ActiveView from(ActiveTradeMonitoring active) {
            return new ActiveView(TradeView.from(active.trade()),
                    active.latestResult().map(ResultView::from));
        }
    }

    public record HistoryView(
            String tradeId,
            Instant timestamp,
            Optional<String> previousState,
            String currentState,
            Optional<String> previousExitRecommendation,
            String currentExitRecommendation,
            BigDecimal previousConfidence,
            BigDecimal currentConfidence,
            BigDecimal previousDirectionalEdge,
            BigDecimal currentDirectionalEdge,
            String explanation,
            List<WarningView> warnings) {

        public static HistoryView from(MonitoringHistoryEntry entry) {
            return new HistoryView(entry.tradeId(), entry.timestamp(),
                    entry.previousState().map(Enum::name), entry.currentState().name(),
                    entry.previousExitRecommendation().map(Enum::name),
                    entry.currentExitRecommendation().name(),
                    entry.previousConfidence(), entry.currentConfidence(),
                    entry.previousDirectionalEdge(), entry.currentDirectionalEdge(),
                    entry.explanation(),
                    entry.warnings().stream().map(WarningView::from).toList());
        }
    }

    public record AlertView(
            String tradeId,
            Instant timestamp,
            String type,
            String message,
            Map<String, String> diagnostics) {

        public static AlertView from(MonitoringAlert alert) {
            return new AlertView(alert.tradeId(), alert.timestamp(), alert.type().name(),
                    alert.message(), alert.diagnostics());
        }
    }
}
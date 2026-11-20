package com.tradingassistant.monitoring.persistence;

import com.tradingassistant.monitoring.domain.ManualTrade;
import com.tradingassistant.monitoring.domain.ManualTradeStatus;
import com.tradingassistant.monitoring.domain.MonitoringAlert;
import com.tradingassistant.monitoring.domain.MonitoringHistoryEntry;
import com.tradingassistant.monitoring.domain.TradeMonitoringResult;
import com.tradingassistant.monitoring.service.MonitoringStore;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** Mongo adapter for the monitoring store port. */
@Component
public class MongoMonitoringStore implements MonitoringStore {

    private static final int MAXIMUM_PAGE_SIZE = 500;

    private final ManualTradeRepository trades;
    private final MonitoringResultRepository results;
    private final MonitoringHistoryRepository history;
    private final MonitoringAlertRepository alerts;

    public MongoMonitoringStore(ManualTradeRepository trades,
                                MonitoringResultRepository results,
                                MonitoringHistoryRepository history,
                                MonitoringAlertRepository alerts) {
        this.trades = trades;
        this.results = results;
        this.history = history;
        this.alerts = alerts;
    }

    @Override
    public List<ManualTrade> activeTrades() {
        return trades.findByStatusOrderByEntryTimestampAsc(ManualTradeStatus.ACTIVE)
                .stream().map(ManualTradeDocument::toDomain).toList();
    }

    @Override
    public Optional<ManualTrade> trade(String tradeId) {
        if (tradeId == null || tradeId.isBlank()) {
            return Optional.empty();
        }
        return trades.findById(tradeId.trim()).map(ManualTradeDocument::toDomain);
    }

    @Override
    public void saveTrade(ManualTrade trade) {
        trades.save(ManualTradeDocument.from(trade));
    }

    @Override
    public Optional<TradeMonitoringResult> latestResult(String tradeId) {
        if (tradeId == null || tradeId.isBlank()) {
            return Optional.empty();
        }
        return results.findById(tradeId.trim()).map(MonitoringResultDocument::toDomain);
    }

    @Override
    public void saveResult(TradeMonitoringResult result) {
        results.save(MonitoringResultDocument.from(result));
    }

    @Override
    public void appendHistory(MonitoringHistoryEntry entry) {
        history.save(MonitoringHistoryDocument.from(entry));
    }

    @Override
    public List<MonitoringHistoryEntry> history(String tradeId, int limit) {
        if (tradeId == null || tradeId.isBlank()) {
            return List.of();
        }
        return history.findByTradeIdOrderByTimestampDesc(tradeId.trim(), page(limit))
                .stream().map(MonitoringHistoryDocument::toDomain).toList();
    }

    @Override
    public List<MonitoringHistoryEntry> history(int limit) {
        return history.findAllByOrderByTimestampDesc(page(limit)).stream()
                .map(MonitoringHistoryDocument::toDomain).toList();
    }

    @Override
    public void appendAlert(MonitoringAlert alert) {
        alerts.save(MonitoringAlertDocument.from(alert));
    }

    @Override
    public List<MonitoringAlert> alerts(int limit) {
        return alerts.findAllByOrderByTimestampDesc(page(limit)).stream()
                .map(MonitoringAlertDocument::toDomain).toList();
    }

    private static PageRequest page(int limit) {
        int size = Math.max(1, Math.min(limit, MAXIMUM_PAGE_SIZE));
        return PageRequest.of(0, size);
    }
}
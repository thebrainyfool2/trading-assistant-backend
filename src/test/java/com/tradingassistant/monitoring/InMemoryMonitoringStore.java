package com.tradingassistant.monitoring;

import com.tradingassistant.monitoring.domain.ManualTrade;
import com.tradingassistant.monitoring.domain.MonitoringAlert;
import com.tradingassistant.monitoring.domain.MonitoringHistoryEntry;
import com.tradingassistant.monitoring.domain.TradeMonitoringResult;
import com.tradingassistant.monitoring.service.MonitoringStore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Deterministic in-memory adapter for service and API tests. */
public final class InMemoryMonitoringStore implements MonitoringStore {

    private final Map<String, ManualTrade> trades = new LinkedHashMap<>();
    private final Map<String, TradeMonitoringResult> results = new LinkedHashMap<>();
    private final List<MonitoringHistoryEntry> history = new ArrayList<>();
    private final List<MonitoringAlert> alerts = new ArrayList<>();

    @Override
    public List<ManualTrade> activeTrades() {
        return trades.values().stream().filter(ManualTrade::active).toList();
    }

    @Override
    public Optional<ManualTrade> trade(String tradeId) {
        return Optional.ofNullable(trades.get(tradeId));
    }

    @Override
    public void saveTrade(ManualTrade trade) {
        trades.put(trade.tradeId(), trade);
    }

    @Override
    public Optional<TradeMonitoringResult> latestResult(String tradeId) {
        return Optional.ofNullable(results.get(tradeId));
    }

    @Override
    public void saveResult(TradeMonitoringResult result) {
        results.put(result.tradeId(), result);
    }

    @Override
    public void appendHistory(MonitoringHistoryEntry entry) {
        history.add(entry);
    }

    @Override
    public List<MonitoringHistoryEntry> history(String tradeId, int limit) {
        return history.stream().filter(entry -> entry.tradeId().equals(tradeId))
                .limit(limit).toList();
    }

    @Override
    public List<MonitoringHistoryEntry> history(int limit) {
        return history.stream().limit(limit).toList();
    }

    @Override
    public void appendAlert(MonitoringAlert alert) {
        alerts.add(alert);
    }

    @Override
    public List<MonitoringAlert> alerts(int limit) {
        return alerts.stream().limit(limit).toList();
    }

    public List<MonitoringAlert> alerts() {
        return List.copyOf(alerts);
    }

    public List<MonitoringHistoryEntry> history() {
        return List.copyOf(history);
    }
}
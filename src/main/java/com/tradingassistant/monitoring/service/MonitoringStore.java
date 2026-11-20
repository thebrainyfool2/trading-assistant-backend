package com.tradingassistant.monitoring.service;

import com.tradingassistant.monitoring.domain.ManualTrade;
import com.tradingassistant.monitoring.domain.MonitoringAlert;
import com.tradingassistant.monitoring.domain.MonitoringHistoryEntry;
import com.tradingassistant.monitoring.domain.TradeMonitoringResult;

import java.util.List;
import java.util.Optional;

/** Persistence port for manual trades, latest results, history and alerts. */
public interface MonitoringStore {

    List<ManualTrade> activeTrades();

    Optional<ManualTrade> trade(String tradeId);

    void saveTrade(ManualTrade trade);

    Optional<TradeMonitoringResult> latestResult(String tradeId);

    void saveResult(TradeMonitoringResult result);

    void appendHistory(MonitoringHistoryEntry entry);

    List<MonitoringHistoryEntry> history(String tradeId, int limit);

    List<MonitoringHistoryEntry> history(int limit);

    void appendAlert(MonitoringAlert alert);

    List<MonitoringAlert> alerts(int limit);
}
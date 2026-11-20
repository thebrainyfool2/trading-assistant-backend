package com.tradingassistant.monitoring.service;

import com.tradingassistant.common.api.ErrorCode;
import com.tradingassistant.monitoring.domain.ActiveTradeMonitoring;
import com.tradingassistant.monitoring.domain.ManualTrade;
import com.tradingassistant.monitoring.domain.MonitoringAlert;
import com.tradingassistant.monitoring.domain.MonitoringHistoryEntry;
import com.tradingassistant.monitoring.domain.TradeMonitoringResult;
import com.tradingassistant.platform.error.PlatformException;

import java.util.List;
import java.util.Optional;

/** Read-only monitoring queries used by the REST edge. */
public final class MonitoringQueryService {

    private final MonitoringStore store;

    public MonitoringQueryService(MonitoringStore store) {
        this.store = store;
    }

    public List<ActiveTradeMonitoring> active() {
        return store.activeTrades().stream()
                .map(trade -> new ActiveTradeMonitoring(trade,
                        store.latestResult(trade.tradeId())))
                .toList();
    }

    public ActiveTradeMonitoring require(String tradeId) {
        ManualTrade trade = store.trade(tradeId)
                .orElseThrow(() -> new PlatformException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Manual trade not found."));
        return new ActiveTradeMonitoring(trade, store.latestResult(trade.tradeId()));
    }

    public Optional<TradeMonitoringResult> latest(String tradeId) {
        return store.latestResult(tradeId);
    }

    public List<MonitoringHistoryEntry> history(String tradeId, int limit) {
        if (tradeId == null || tradeId.isBlank()) {
            return store.history(limit);
        }
        require(tradeId);
        return store.history(tradeId.trim(), limit);
    }

    public List<MonitoringAlert> alerts(int limit) {
        return store.alerts(limit);
    }
}
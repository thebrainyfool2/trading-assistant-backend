package com.tradingassistant.monitoring.domain;

import java.util.Objects;
import java.util.Optional;

/** Read projection joining an active manual trade to its latest result. */
public record ActiveTradeMonitoring(
        ManualTrade trade,
        Optional<TradeMonitoringResult> latestResult) {

    public ActiveTradeMonitoring {
        trade = Objects.requireNonNull(trade, "trade must not be null");
        latestResult = latestResult == null ? Optional.empty() : latestResult;
    }
}
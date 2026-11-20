package com.tradingassistant.monitoring.persistence;

import com.tradingassistant.monitoring.MonitoringTestFixtures;
import com.tradingassistant.monitoring.domain.MonitoringAlert;
import com.tradingassistant.monitoring.domain.MonitoringAlertType;
import com.tradingassistant.monitoring.domain.MonitoringHistoryEntry;
import com.tradingassistant.monitoring.domain.TradeMonitoringResult;
import com.tradingassistant.monitoring.engine.TradeMonitoringEngine;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MonitoringPersistenceTest {

    @Test
    void manualTradeDocumentRoundTrips() {
        var trade = MonitoringTestFixtures.callTrade();

        assertThat(ManualTradeDocument.from(trade).toDomain()).isEqualTo(trade);
    }

    @Test
    void resultDocumentRoundTripsEveryExplanationAndDelta() {
        TradeMonitoringResult result = new TradeMonitoringEngine(
                MonitoringTestFixtures.config()).evaluate(
                MonitoringTestFixtures.context(MonitoringTestFixtures.callTrade(),
                        MonitoringTestFixtures.defaultCall()));

        assertThat(MonitoringResultDocument.from(result).toDomain()).isEqualTo(result);
    }

    @Test
    void historyDocumentRoundTrips() {
        MonitoringHistoryEntry entry = new MonitoringHistoryEntry(
                MonitoringTestFixtures.TRADE_ID, MonitoringTestFixtures.START,
                Optional.empty(),
                com.tradingassistant.monitoring.domain.MonitoringState.HEALTHY,
                Optional.empty(),
                com.tradingassistant.monitoring.domain.ExitRecommendation.HOLD,
                new java.math.BigDecimal("72"), new java.math.BigDecimal("72"),
                new java.math.BigDecimal("30"), new java.math.BigDecimal("30"),
                "healthy", java.util.List.of());

        assertThat(MonitoringHistoryDocument.from(entry).toDomain()).isEqualTo(entry);
    }

    @Test
    void alertDocumentRoundTrips() {
        MonitoringAlert alert = new MonitoringAlert(MonitoringTestFixtures.TRADE_ID,
                MonitoringTestFixtures.START, MonitoringAlertType.SESSION_CLOSED,
                "market session is closed", Map.of("session", "CLOSED"));

        assertThat(MonitoringAlertDocument.from(alert).toDomain()).isEqualTo(alert);
    }
}
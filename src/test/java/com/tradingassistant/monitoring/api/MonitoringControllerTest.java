package com.tradingassistant.monitoring.api;

import com.tradingassistant.common.error.GlobalExceptionHandler;
import com.tradingassistant.monitoring.InMemoryMonitoringStore;
import com.tradingassistant.monitoring.MonitoringTestFixtures;
import com.tradingassistant.monitoring.domain.ManualTrade;
import com.tradingassistant.monitoring.domain.MonitoringAlert;
import com.tradingassistant.monitoring.domain.MonitoringAlertType;
import com.tradingassistant.monitoring.domain.MonitoringHistoryEntry;
import com.tradingassistant.monitoring.domain.TradeMonitoringResult;
import com.tradingassistant.monitoring.engine.TradeMonitoringEngine;
import com.tradingassistant.monitoring.service.MonitoringQueryService;
import com.tradingassistant.platform.error.PlatformExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.Optional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MonitoringControllerTest {

    private final InMemoryMonitoringStore store = new InMemoryMonitoringStore();
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                    new MonitoringController(new MonitoringQueryService(store)))
            .setControllerAdvice(new GlobalExceptionHandler(),
                    new PlatformExceptionHandler())
            .build();

    @BeforeEach
    void seed() {
        ManualTrade trade = MonitoringTestFixtures.callTrade();
        TradeMonitoringResult result = new TradeMonitoringEngine(
                MonitoringTestFixtures.config()).evaluate(
                MonitoringTestFixtures.context(trade, MonitoringTestFixtures.defaultCall()));
        store.saveTrade(trade);
        store.saveResult(result);
        store.appendHistory(new MonitoringHistoryEntry(result.tradeId(), result.timestamp(),
                Optional.empty(), result.monitoringState(), Optional.empty(),
                result.exitRecommendation(), result.currentConfidence(),
                result.currentConfidence(), result.currentDirectionalEdge(),
                result.currentDirectionalEdge(), result.explanation(), result.warnings()));
        store.appendAlert(new MonitoringAlert(result.tradeId(), result.timestamp(),
                MonitoringAlertType.MONITORING_STARTED, "monitoring started", Map.of()));
    }

    @Test
    void activeEndpointReturnsTradeAndLatestResult() throws Exception {
        mockMvc.perform(get("/api/v1/monitoring/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].trade.tradeId")
                        .value(MonitoringTestFixtures.TRADE_ID))
                .andExpect(jsonPath("$.data[0].latestResult.monitoringState")
                        .value("HEALTHY"))
                .andExpect(jsonPath("$.data[0].latestResult.exitRecommendation")
                        .value("HOLD"));
    }

    @Test
    void historyEndpointIsReadOnlyAndBounded() throws Exception {
        mockMvc.perform(get("/api/v1/monitoring/history").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].currentState").value("HEALTHY"));
    }

    @Test
    void singleTradeEndpointReturnsTheView() throws Exception {
        mockMvc.perform(get("/api/v1/monitoring/{tradeId}",
                        MonitoringTestFixtures.TRADE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trade.optionSymbol").value("NIFTY-CE-500"));
    }

    @Test
    void unknownTradeReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/monitoring/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void alertsEndpointReturnsAlertHistory() throws Exception {
        mockMvc.perform(get("/api/v1/monitoring/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].type").value("MONITORING_STARTED"));
    }
}
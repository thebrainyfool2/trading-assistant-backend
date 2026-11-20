package com.tradingassistant.platform.trade;

import com.tradingassistant.common.error.GlobalExceptionHandler;
import com.tradingassistant.market.domain.Currency;
import com.tradingassistant.market.domain.Instrument;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.InstrumentType;
import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.market.domain.Segment;
import com.tradingassistant.monitoring.api.MonitoringDtos;
import com.tradingassistant.platform.error.PlatformExceptionHandler;
import com.tradingassistant.platform.instruments.InstrumentController;
import com.tradingassistant.platform.instruments.InstrumentDtos;
import com.tradingassistant.platform.instruments.InstrumentSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ActionableControllerTest {

    private final InstrumentSearchService search = mock(InstrumentSearchService.class);
    private final RecommendationQueryService recommendations =
            mock(RecommendationQueryService.class);
    private final ManualTradeCommandService commands =
            mock(ManualTradeCommandService.class);
    private final OptionPremiumService premiums = mock(OptionPremiumService.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                    new InstrumentController(search),
                    new RecommendationController(recommendations),
                    new ManualTradeController(commands),
                    new OptionPremiumController(premiums))
            .setControllerAdvice(new GlobalExceptionHandler(),
                    new PlatformExceptionHandler())
            .build();

    @Test
    void searchReturnsTheStableInstrumentReference() throws Exception {
        when(search.search(anyString(), any(), any(), anyInt())).thenReturn(List.of(
                new Instrument(new InstrumentKey(Segment.NSE_CASH, "2885"),
                        "RELIANCE-EQ", "RELIANCE-EQ", "RELIANCE",
                        InstrumentType.EQUITY, new BigDecimal("0.05"), 1L,
                        null, null, Currency.INR, null, null, null, null)));

        mockMvc.perform(get("/api/v1/instruments/search").param("q", "reliance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].segment").value("NSE_CASH"))
                .andExpect(jsonPath("$.data[0].symbolToken").value("2885"))
                .andExpect(jsonPath("$.data[0].displayName").value("RELIANCE"));
    }

    @Test
    void recommendationReturnsTheDecisionAndOptionalPlan() throws Exception {
        RecommendationDtos.RecommendationView view =
                new RecommendationDtos.RecommendationView(
                        com.tradingassistant.recommendation.domain.RecommendationType.BUY_CALL,
                        "NSE_CASH:3045", Interval.FIVE_MINUTE,
                        Instant.parse("2026-11-18T03:45:00Z"), new BigDecimal("80"),
                        new BigDecimal("72"), new BigDecimal("30"), List.of(),
                        Optional.empty(), "fixture", Map.of(), Optional.empty());
        when(recommendations.latest(anyInt())).thenReturn(List.of(view));
        when(recommendations.require(any(), any())).thenReturn(view);

        mockMvc.perform(get("/api/v1/recommendations")
                        .param("instrument", "NSE_CASH:3045")
                        .param("interval", "FIVE_MINUTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].type").value("BUY_CALL"))
                .andExpect(jsonPath("$.data[0].confidence").value(72));
    }

    @Test
    void manualFillUsesTheWriteRoute() throws Exception {
        when(commands.register(any())).thenReturn(new MonitoringDtos.TradeView(
                "trade-1", "3045", "FIVE_MINUTE", "BUY_CALL", "SBIN26NOV500CE",
                new BigDecimal("500"), LocalDate.of(2026, 11, 26),
                Instant.parse("2026-11-18T03:50:00Z"), new BigDecimal("12.50"),
                new BigDecimal("75"), "", "ACTIVE", Optional.of(new BigDecimal("18")),
                Optional.of(new BigDecimal("8")),
                Optional.of(Instant.parse("2026-11-18T03:45:00Z"))));

        mockMvc.perform(post("/api/v1/monitoring/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "instrument": "NSE_CASH:3045",
                                  "recommendationType": "BUY_CALL",
                                  "interval": "FIVE_MINUTE",
                                  "recommendationTimestamp": "2026-11-18T03:45:00Z",
                                  "optionSymbol": "SBIN26NOV500CE",
                                  "strike": 500,
                                  "expiry": "2026-11-26",
                                  "entryPrice": 12.5,
                                  "quantity": 75
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.tradeId").value("trade-1"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void premiumStatusIsReadOnly() throws Exception {
        when(premiums.active()).thenReturn(List.of(new OptionPremiumSnapshot(
                "trade-1", "NSE_FO:123", "SBIN26NOV500CE", new BigDecimal("12.50"),
                Optional.of(new BigDecimal("18")), Optional.of(new BigDecimal("412.50")),
                Optional.of(new BigDecimal("44.00")), Optional.of(new BigDecimal("18")),
                Optional.of(new BigDecimal("8")), PremiumStatus.TARGET_HIT, true,
                Instant.parse("2026-11-18T04:00:00Z"))));

        mockMvc.perform(get("/api/v1/monitoring/premium"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("TARGET_HIT"))
                .andExpect(jsonPath("$.data[0].sessionOpen").value(true));
    }
}

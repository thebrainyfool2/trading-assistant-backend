package com.tradingassistant.platform.api;

import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.market.domain.Segment;
import com.tradingassistant.platform.PlatformTestFixtures;
import com.tradingassistant.platform.readmodel.MarketDataFreshnessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MarketFreshnessControllerTest {

    private final MarketDataFreshnessService freshness = mock(MarketDataFreshnessService.class);
    private final MockMvc mockMvc = WebTestSupport.mvc(new MarketFreshnessController(freshness));

    @BeforeEach
    void signIn() {
        WebTestSupport.authenticate(PlatformTestFixtures.principal());
    }

    @AfterEach
    void signOut() {
        WebTestSupport.clearAuthentication();
    }

    @Test
    void exposesTheProjectionWithoutThresholds() throws Exception {
        when(freshness.snapshot()).thenReturn(List.of(new MarketDataFreshnessService.Freshness(
                new InstrumentKey(Segment.NSE_CASH, "2885"),
                MarketDataFreshnessService.FreshnessStatus.FRESH,
                PlatformTestFixtures.NOW.minusSeconds(30), PlatformTestFixtures.NOW.minusSeconds(300),
                Interval.FIVE_MINUTE, 30L, 300L, new BigDecimal("2500.50"),
                MarketDataFreshnessService.MarketSessionState.OPEN)));

        mockMvc.perform(get("/api/v1/market/freshness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].segment").value("NSE_CASH"))
                .andExpect(jsonPath("$.data[0].symbolToken").value("2885"))
                .andExpect(jsonPath("$.data[0].status").value("FRESH"))
                .andExpect(jsonPath("$.data[0].session").value("OPEN"))
                .andExpect(jsonPath("$.data[0].candleInterval").value("FIVE_MINUTE"))
                .andExpect(jsonPath("$.data[0].tickAgeSeconds").value(30));
    }
}

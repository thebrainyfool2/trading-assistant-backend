package com.tradingassistant.platform.api;

import com.tradingassistant.platform.PlatformTestFixtures;
import com.tradingassistant.platform.executionapp.PortfolioQueryService;
import com.tradingassistant.platform.history.PortfolioPositionSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PortfolioControllerTest {

    private final PortfolioQueryService portfolio = mock(PortfolioQueryService.class);
    private final MockMvc mockMvc = WebTestSupport.mvc(
            new PortfolioController(portfolio), new PositionController(portfolio));

    @BeforeEach
    void signIn() {
        WebTestSupport.authenticate(PlatformTestFixtures.principal());
    }

    @AfterEach
    void signOut() {
        WebTestSupport.clearAuthentication();
    }

    @Test
    void portfolioNamesItsSource() throws Exception {
        when(portfolio.current()).thenReturn(view());

        mockMvc.perform(get("/api/v1/portfolio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("LIVE"))
                .andExpect(jsonPath("$.data.capital").value(100000))
                .andExpect(jsonPath("$.data.positions[0].positionId").value("POS-1"))
                .andExpect(jsonPath("$.data.positions[0].remainingQuantity").value(100));
    }

    @Test
    void positionsExposeTheOpenBook() throws Exception {
        when(portfolio.positions()).thenReturn(
                List.of(PortfolioPositionSnapshot.from(
                        PlatformTestFixtures.openPosition())));

        mockMvc.perform(get("/api/v1/positions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].direction").value("LONG"));
    }

    @Test
    void portfolioPositionsAliasIsAvailable() throws Exception {
        when(portfolio.positions()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/portfolio/positions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    private static PortfolioQueryService.PortfolioView view() {
        return new PortfolioQueryService.PortfolioView("LIVE", null,
                PlatformTestFixtures.amount("100000"),
                PlatformTestFixtures.amount("90000"),
                PlatformTestFixtures.amount("1000"),
                PlatformTestFixtures.amount("9000"), PlatformTestFixtures.amount("0"),
                PlatformTestFixtures.amount("0"), PlatformTestFixtures.amount("45000"),
                1, 0, List.of(PortfolioPositionSnapshot.from(
                PlatformTestFixtures.openPosition())));
    }
}

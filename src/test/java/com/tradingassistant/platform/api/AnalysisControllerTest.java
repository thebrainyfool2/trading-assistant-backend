package com.tradingassistant.platform.api;

import com.tradingassistant.execution.ExecutionTestFixtures;
import com.tradingassistant.platform.PlatformTestFixtures;
import com.tradingassistant.platform.readmodel.AnalysisQueryService;
import com.tradingassistant.risk.RiskTestFixtures;
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

class AnalysisControllerTest {

    private final AnalysisQueryService analysis = mock(AnalysisQueryService.class);
    private final MockMvc mockMvc = WebTestSupport.mvc(
            new MarketController(analysis),
            new OptionsController(analysis),
            new StrategyController(analysis),
            new RiskController(analysis));

    @BeforeEach
    void signIn() {
        WebTestSupport.authenticate(PlatformTestFixtures.principal());
        when(analysis.now()).thenReturn(PlatformTestFixtures.NOW);
    }

    @AfterEach
    void signOut() {
        WebTestSupport.clearAuthentication();
    }

    @Test
    void overviewCombinesEveryPublishedDescription() throws Exception {
        when(analysis.markets(25)).thenReturn(List.of(
                RiskTestFixtures.marketAssessment()));
        when(analysis.options(25)).thenReturn(List.of(
                RiskTestFixtures.optionsAssessment()));
        when(analysis.strategies(25)).thenReturn(List.of(
                RiskTestFixtures.strategyEvaluation()));
        when(analysis.risks(25)).thenReturn(List.of(
                ExecutionTestFixtures.assessment()));
        when(analysis.qualifications(25)).thenReturn(List.of(
                RiskTestFixtures.qualification()));
        when(analysis.readModelEntries()).thenReturn(5);
        when(analysis.engineAvailable()).thenReturn(true);

        mockMvc.perform(get("/api/v1/market/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.engineAvailable").value(true))
                .andExpect(jsonPath("$.data.readModelEntries").value(5))
                .andExpect(jsonPath("$.data.markets.length()").value(1))
                .andExpect(jsonPath("$.data.options.length()").value(1))
                .andExpect(jsonPath("$.data.strategies.length()").value(1))
                .andExpect(jsonPath("$.data.risks.length()").value(1))
                .andExpect(jsonPath("$.data.qualifications.length()").value(1));
    }

    @Test
    void marketAssessmentExposesDescriptiveComponents() throws Exception {
        when(analysis.markets(10)).thenReturn(List.of(
                RiskTestFixtures.marketAssessment()));

        mockMvc.perform(get("/api/v1/market/assessments").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("READY"))
                .andExpect(jsonPath("$.data[0].trend.state").value("SIDEWAYS"))
                .andExpect(jsonPath("$.data[0].structureBias").value("RANGING"))
                .andExpect(jsonPath("$.data[0].sessionPhase").value("REGULAR"));
    }

    @Test
    void optionAssessmentExposesChainAndVolatilityFields() throws Exception {
        when(analysis.options(25)).thenReturn(List.of(
                RiskTestFixtures.optionsAssessment()));

        mockMvc.perform(get("/api/v1/options/assessments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].underlying").value("SBIN"))
                .andExpect(jsonPath("$.data[0].expiryCount").isNumber())
                .andExpect(jsonPath("$.data[0].netDelta").isNumber());
    }

    @Test
    void strategyEvaluationsAndDefinitionsAreReadable() throws Exception {
        when(analysis.strategies(25)).thenReturn(List.of(
                RiskTestFixtures.strategyEvaluation()));
        when(analysis.definitions()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/strategy/evaluations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].strategyId").value("EMA_TREND"))
                .andExpect(jsonPath("$.data[0].rules").isArray());

        mockMvc.perform(get("/api/v1/strategy/definitions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void riskAssessmentsAndQualificationsAreReadable() throws Exception {
        when(analysis.risks(25)).thenReturn(List.of(
                ExecutionTestFixtures.assessment()));
        when(analysis.qualifications(25)).thenReturn(List.of(
                RiskTestFixtures.qualification()));

        mockMvc.perform(get("/api/v1/risk/assessments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].quantity").value(100))
                .andExpect(jsonPath("$.data[0].status").value("WITHIN_LIMITS"));

        mockMvc.perform(get("/api/v1/risk/qualifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].strategyId").value("EMA_TREND"))
                .andExpect(jsonPath("$.data[0].status").value("QUALIFIED"));
    }
}

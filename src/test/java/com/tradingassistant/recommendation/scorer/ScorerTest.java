package com.tradingassistant.recommendation.scorer;

import com.tradingassistant.recommendation.RecommendationTestFixtures;
import com.tradingassistant.recommendation.config.RecommendationConfig;
import com.tradingassistant.recommendation.domain.DirectionalBias;
import com.tradingassistant.recommendation.domain.RecommendationContext;
import com.tradingassistant.recommendation.domain.ScorerId;
import com.tradingassistant.recommendation.domain.ScorerResult;
import com.tradingassistant.recommendation.registry.ScorerRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScorerTest {

    private final RecommendationConfig config = RecommendationTestFixtures.config();
    private final ScorerRegistry registry = ScorerRegistry.standard(config);

    @Test
    void everyScorerReturnsAnExplainableBoundedScore() {
        RecommendationContext context = RecommendationTestFixtures.bullishContext();

        for (ScorerId id : ScorerId.values()) {
            ScorerResult result = registry.get(id).score(context);
            assertThat(result.scorerId()).isEqualTo(id);
            assertThat(result.score()).isBetween(
                    java.math.BigDecimal.ZERO, result.maximum());
            assertThat(result.explanation()).isNotBlank();
            assertThat(result.diagnostics()).isNotNull();
        }
    }

    @Test
    void trendReadsBullishAndBearishAssessments() {
        assertThat(registry.get(ScorerId.TREND)
                .score(RecommendationTestFixtures.bullishContext()).score())
                .isEqualByComparingTo("100");
        assertThat(registry.get(ScorerId.TREND)
                .score(RecommendationTestFixtures.bearishContext()).bias())
                .isEqualTo(DirectionalBias.BEARISH);
        assertThat(registry.get(ScorerId.TREND)
                .score(RecommendationTestFixtures.neutralContext()).score())
                .isEqualByComparingTo("40");
    }

    @Test
    void momentumRequiresDirectionalMomentum() {
        ScorerResult bullish = registry.get(ScorerId.MOMENTUM)
                .score(RecommendationTestFixtures.bullishContext());
        ScorerResult bearish = registry.get(ScorerId.MOMENTUM)
                .score(RecommendationTestFixtures.bearishContext());
        ScorerResult neutral = registry.get(ScorerId.MOMENTUM)
                .score(RecommendationTestFixtures.neutralContext());

        assertThat(bullish.score()).isEqualByComparingTo("100");
        assertThat(bullish.bias()).isEqualTo(DirectionalBias.BULLISH);
        assertThat(bearish.bias()).isEqualTo(DirectionalBias.BEARISH);
        assertThat(neutral.score()).isEqualByComparingTo("45");
    }

    @Test
    void optionsScorersReadExistingSummaries() {
        RecommendationContext bullish = RecommendationTestFixtures.bullishContext();

        assertThat(registry.get(ScorerId.LIQUIDITY).score(bullish).score())
                .isEqualByComparingTo("100");
        assertThat(registry.get(ScorerId.OPEN_INTEREST).score(bullish).score())
                .isEqualByComparingTo("100");
        assertThat(registry.get(ScorerId.PCR).score(bullish).score())
                .isEqualByComparingTo("100");
        assertThat(registry.get(ScorerId.IV).score(bullish).score())
                .isEqualByComparingTo("100");
        assertThat(registry.get(ScorerId.IV_RANK).score(bullish).score())
                .isEqualByComparingTo("100");
        assertThat(registry.get(ScorerId.GREEKS).score(bullish).score())
                .isEqualByComparingTo("100");
        assertThat(registry.get(ScorerId.EXPECTED_MOVE).score(bullish).score())
                .isEqualByComparingTo("100");
    }

    @Test
    void optionScorersExplainMissingOptions() {
        RecommendationContext missing = new RecommendationContext(
                RecommendationTestFixtures.bullishMarket(), java.util.Optional.empty(),
                java.util.Optional.empty(), RecommendationTestFixtures.indicators(),
                java.util.Optional.of(RecommendationTestFixtures.candle()),
                java.util.List.of(RecommendationTestFixtures.strategy()),
                java.util.List.of(RecommendationTestFixtures.qualification()),
                java.util.List.of(RecommendationTestFixtures.risk()),
                RecommendationTestFixtures.freshness());

        assertThat(registry.get(ScorerId.LIQUIDITY).score(missing).score())
                .isEqualByComparingTo("0");
        assertThat(registry.get(ScorerId.PCR).score(missing).bias())
                .isEqualTo(DirectionalBias.UNKNOWN);
    }

    @Test
    void structureAndAlignmentReadExistingSignals() {
        RecommendationContext bullish = RecommendationTestFixtures.bullishContext();

        assertThat(registry.get(ScorerId.MARKET_STRUCTURE).score(bullish).score())
                .isEqualByComparingTo("100");
        assertThat(registry.get(ScorerId.BREAKOUT).score(bullish).score())
                .isEqualByComparingTo("100");
        assertThat(registry.get(ScorerId.TREND_ALIGNMENT).score(bullish).score())
                .isEqualByComparingTo("80");
    }

    @Test
    void riskQualificationStrategySessionAndFreshnessReadExistingOutcomes() {
        RecommendationContext bullish = RecommendationTestFixtures.bullishContext();

        assertThat(registry.get(ScorerId.RISK_REWARD).score(bullish).score())
                .isEqualByComparingTo("100");
        assertThat(registry.get(ScorerId.QUALIFICATION).score(bullish).score())
                .isEqualByComparingTo("100");
        assertThat(registry.get(ScorerId.STRATEGY_AGREEMENT).score(bullish).score())
                .isEqualByComparingTo("100");
        assertThat(registry.get(ScorerId.MARKET_SESSION).score(bullish).score())
                .isEqualByComparingTo("100");
        assertThat(registry.get(ScorerId.FRESHNESS).score(bullish).score())
                .isEqualByComparingTo("100");
    }

    @Test
    void everyWeightIsConfigurableAndZeroIsAllowed() {
        RecommendationConfig zero = RecommendationTestFixtures
                .configWithZeroWeight(ScorerId.LIQUIDITY);
        ScorerRegistry zeroRegistry = ScorerRegistry.standard(zero);

        assertThat(zeroRegistry.size()).isEqualTo(ScorerId.values().length);
        assertThat(zero.weight(ScorerId.LIQUIDITY)).isEqualByComparingTo("0");
        assertThat(zero.totalWeight()).isPositive();
    }
}

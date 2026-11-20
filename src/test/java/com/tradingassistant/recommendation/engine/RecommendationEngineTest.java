package com.tradingassistant.recommendation.engine;

import com.tradingassistant.options.OptionsTestFixtures;
import com.tradingassistant.market.domain.OptionType;
import com.tradingassistant.recommendation.RecommendationTestFixtures;
import com.tradingassistant.recommendation.config.RecommendationConfig;
import com.tradingassistant.recommendation.domain.Recommendation;
import com.tradingassistant.recommendation.domain.RecommendationContext;
import com.tradingassistant.recommendation.domain.RecommendationType;
import com.tradingassistant.recommendation.domain.ScorerId;
import com.tradingassistant.recommendation.exception.RecommendationValidationException;
import com.tradingassistant.recommendation.registry.ScorerRegistry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecommendationEngineTest {

    private final RecommendationConfig config = RecommendationTestFixtures.config();
    private final RecommendationEngine engine = new RecommendationEngine(
            ScorerRegistry.standard(config), config);

    @Test
    void producesABuyCallFromBullishEngineOutputs() {
        Recommendation recommendation = engine.evaluate(
                RecommendationTestFixtures.bullishContext());

        assertThat(recommendation.type()).isEqualTo(RecommendationType.BUY_CALL);
        assertThat(recommendation.components()).hasSize(ScorerId.values().length);
        assertThat(recommendation.option()).isPresent();
        assertThat(recommendation.option().get().optionType()).isEqualTo(OptionType.CALL);
        assertThat(recommendation.option().get().strike())
                .isEqualByComparingTo("500");
        assertThat(recommendation.option().get().expiry())
                .isEqualTo(OptionsTestFixtures.EXPIRY);
        assertThat(recommendation.confidence()).isPositive();
        assertThat(recommendation.summary()).contains("BUY CALL");
        assertThat(recommendation.components())
                .allSatisfy(component -> {
                    assertThat(component.explanation()).isNotBlank();
                    assertThat(component.maximum()).isEqualByComparingTo("100");
                    assertThat(component.weightedContribution()).isNotNegative();
                });
    }

    @Test
    void producesABuyPutFromBearishEngineOutputs() {
        Recommendation recommendation = engine.evaluate(
                RecommendationTestFixtures.bearishContext());

        assertThat(recommendation.type()).isEqualTo(RecommendationType.BUY_PUT);
        assertThat(recommendation.option()).get()
                .extracting(selection -> selection.optionType())
                .isEqualTo(OptionType.PUT);
        assertThat(recommendation.option()).get()
                .extracting(selection -> selection.strike())
                .isEqualTo(new BigDecimal("500"));
    }

    @Test
    void producesNoTradeWhenDirectionalEvidenceIsNeutral() {
        Recommendation recommendation = engine.evaluate(
                RecommendationTestFixtures.neutralContext());

        assertThat(recommendation.type()).isEqualTo(RecommendationType.NO_TRADE);
        assertThat(recommendation.option()).isEmpty();
        assertThat(recommendation.diagnostics().get("decisionReason"))
                .contains("directional edge");
    }

    @Test
    void producesNoTradeWhenQualificationIsNotQualified() {
        RecommendationContext context = RecommendationTestFixtures.context(
                RecommendationTestFixtures.bullishMarket(),
                RecommendationTestFixtures.bullishOptions(),
                RecommendationTestFixtures.disqualified(),
                RecommendationTestFixtures.risk(),
                RecommendationTestFixtures.freshness());

        Recommendation recommendation = engine.evaluate(context);

        assertThat(recommendation.type()).isEqualTo(RecommendationType.NO_TRADE);
        assertThat(recommendation.diagnostics().get("decisionReason"))
                .contains("QUALIFIED");
    }

    @Test
    void producesNoTradeWithoutOptionsAndChain() {
        RecommendationContext context = new RecommendationContext(
                RecommendationTestFixtures.bullishMarket(), Optional.empty(),
                Optional.empty(), RecommendationTestFixtures.indicators(),
                Optional.of(RecommendationTestFixtures.candle()),
                List.of(RecommendationTestFixtures.strategy()),
                List.of(RecommendationTestFixtures.qualification()),
                List.of(RecommendationTestFixtures.risk()),
                RecommendationTestFixtures.freshness());

        Recommendation recommendation = engine.evaluate(context);

        assertThat(recommendation.type()).isEqualTo(RecommendationType.NO_TRADE);
        assertThat(recommendation.option()).isEmpty();
    }

    @Test
    void rejectsStaleData() {
        RecommendationContext context = RecommendationTestFixtures.context(
                RecommendationTestFixtures.bullishMarket(),
                RecommendationTestFixtures.bullishOptions(),
                RecommendationTestFixtures.qualification(),
                RecommendationTestFixtures.risk(),
                RecommendationTestFixtures.staleFreshness());

        assertThatThrownBy(() -> engine.evaluate(context))
                .isInstanceOf(RecommendationValidationException.class)
                .hasMessageContaining("not fresh");
    }

    @Test
    void rejectsMissingQualification() {
        RecommendationContext context = new RecommendationContext(
                RecommendationTestFixtures.bullishMarket(),
                Optional.of(RecommendationTestFixtures.bullishOptions()),
                Optional.of(RecommendationTestFixtures.chain()),
                RecommendationTestFixtures.indicators(),
                Optional.of(RecommendationTestFixtures.candle()),
                List.of(RecommendationTestFixtures.strategy()), List.of(),
                List.of(RecommendationTestFixtures.risk()),
                RecommendationTestFixtures.freshness());

        assertThatThrownBy(() -> engine.evaluate(context))
                .isInstanceOf(RecommendationValidationException.class)
                .hasMessageContaining("qualification");
    }

    @Test
    void rejectsAnUnsupportedExpiryPolicy() {
        RecommendationConfig monthlyOnly = new RecommendationConfig(true,
                RecommendationTestFixtures.config().weights(), BigDecimal.ZERO,
                BigDecimal.ONE, new BigDecimal("1.20"), new BigDecimal("0.80"),
                new BigDecimal("80"), new BigDecimal("20"),
                java.util.Set.of(com.tradingassistant.options.domain.ExpiryKind.WEEKLY),
                com.tradingassistant.recommendation.config.ExpirySelection.NEAREST,
                new com.tradingassistant.recommendation.config.StrikeSelectionPolicy(
                        com.tradingassistant.recommendation.config.StrikeMode.ATM, 0, 5));
        RecommendationEngine monthlyEngine = new RecommendationEngine(
                ScorerRegistry.standard(monthlyOnly), monthlyOnly);

        assertThatThrownBy(() -> monthlyEngine.evaluate(
                RecommendationTestFixtures.bullishContext()))
                .isInstanceOf(RecommendationValidationException.class)
                .hasMessageContaining("allowed expiry");
    }

    @Test
    void isDeterministicForIdenticalImmutableInputs() {
        Recommendation first = engine.evaluate(
                RecommendationTestFixtures.bullishContext());
        Recommendation second = engine.evaluate(
                RecommendationTestFixtures.bullishContext());

        assertThat(first).isEqualTo(second);
        assertThat(first.diagnostics()).isEqualTo(second.diagnostics());
    }

    @Test
    void noTradeWhenConfidenceIsBelowTheConfiguredMinimum() {
        RecommendationConfig strict = RecommendationTestFixtures.config(1,
                new BigDecimal("100"), BigDecimal.ONE);
        RecommendationEngine strictEngine = new RecommendationEngine(
                ScorerRegistry.standard(strict), strict);

        Recommendation recommendation = strictEngine.evaluate(
                RecommendationTestFixtures.bullishContext());

        assertThat(recommendation.type()).isEqualTo(RecommendationType.NO_TRADE);
        assertThat(recommendation.diagnostics().get("decisionReason"))
                .contains("confidence");
    }

    @Test
    void noTradeWhenDirectionalEdgeIsBelowTheConfiguredMinimum() {
        RecommendationConfig strict = RecommendationTestFixtures.config(1,
                BigDecimal.ZERO, new BigDecimal("999"));
        RecommendationEngine strictEngine = new RecommendationEngine(
                ScorerRegistry.standard(strict), strict);

        Recommendation recommendation = strictEngine.evaluate(
                RecommendationTestFixtures.bullishContext());

        assertThat(recommendation.type()).isEqualTo(RecommendationType.NO_TRADE);
        assertThat(recommendation.option()).isEmpty();
    }

    @Test
    void aZeroWeightRemovesThatScorersContribution() {
        RecommendationConfig zeroTrend = RecommendationTestFixtures
                .configWithZeroWeight(ScorerId.TREND);
        RecommendationEngine zeroTrendEngine = new RecommendationEngine(
                ScorerRegistry.standard(zeroTrend), zeroTrend);

        Recommendation recommendation = zeroTrendEngine.evaluate(
                RecommendationTestFixtures.bullishContext());

        assertThat(recommendation.component(ScorerId.TREND)).get()
                .extracting(component -> component.weightedContribution())
                .isEqualTo(BigDecimal.ZERO);
    }

    @Test
    void rejectsOptionsFromAnotherTimestamp() {
        var options = new com.tradingassistant.options.domain.OptionsAssessment(
                RecommendationTestFixtures.SPOT, RecommendationTestFixtures.UNDERLYING,
                RecommendationTestFixtures.START.plusSeconds(60),
                com.tradingassistant.intelligence.domain.AssessmentStatus.READY, false,
                RecommendationTestFixtures.bullishOptions().chain(),
                RecommendationTestFixtures.bullishOptions().volatility(),
                RecommendationTestFixtures.bullishOptions().greeks(),
                RecommendationTestFixtures.bullishOptions().openInterest(),
                RecommendationTestFixtures.bullishOptions().putCallRatio(),
                RecommendationTestFixtures.bullishOptions().expectedMove(),
                RecommendationTestFixtures.bullishOptions().liquidity(),
                RecommendationTestFixtures.bullishOptions().maxPain(),
                RecommendationTestFixtures.bullishOptions().session(), java.util.Map.of());
        RecommendationContext context = RecommendationTestFixtures.context(
                RecommendationTestFixtures.bullishMarket(), options,
                RecommendationTestFixtures.qualification(),
                RecommendationTestFixtures.risk(), RecommendationTestFixtures.freshness());

        assertThatThrownBy(() -> engine.evaluate(context))
                .isInstanceOf(RecommendationValidationException.class)
                .hasMessageContaining("options assessment");
    }

    @Test
    void confidenceIsTheWeightedShareOfAwardedPoints() {
        Recommendation recommendation = engine.evaluate(
                RecommendationTestFixtures.bullishContext());
        BigDecimal expected = recommendation.components().stream()
                .map(component -> component.weightedContribution())
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(config.totalWeight(), java.math.MathContext.DECIMAL128)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, java.math.RoundingMode.HALF_UP);

        assertThat(recommendation.confidence()).isEqualByComparingTo(expected);
    }
}

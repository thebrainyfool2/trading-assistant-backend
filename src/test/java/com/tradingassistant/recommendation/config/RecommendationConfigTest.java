package com.tradingassistant.recommendation.config;

import com.tradingassistant.options.domain.ExpiryKind;
import com.tradingassistant.recommendation.RecommendationTestFixtures;
import com.tradingassistant.recommendation.domain.ScorerId;
import com.tradingassistant.recommendation.exception.RecommendationValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecommendationConfigTest {

    @Test
    void acceptsAValidConfiguration() {
        RecommendationConfig config = RecommendationTestFixtures.config();

        assertThat(config.totalWeight()).isPositive();
        assertThat(config.weights()).hasSize(ScorerId.values().length);
        assertThat(config.strikeSelection().mode()).isEqualTo(StrikeMode.ATM);
    }

    @Test
    void rejectsNegativeWeights() {
        Map<ScorerId, BigDecimal> weights = weights();
        weights.put(ScorerId.TREND, new BigDecimal("-1"));

        assertThatThrownBy(() -> build(weights))
                .isInstanceOf(RecommendationValidationException.class)
                .hasMessageContaining("negative weight");
    }

    @Test
    void rejectsMissingWeights() {
        Map<ScorerId, BigDecimal> weights = weights();
        weights.remove(ScorerId.FRESHNESS);

        assertThatThrownBy(() -> build(weights))
                .isInstanceOf(RecommendationValidationException.class)
                .hasMessageContaining("missing required scorer");
    }

    @Test
    void rejectsAZeroTotalWeight() {
        Map<ScorerId, BigDecimal> weights = new EnumMap<>(ScorerId.class);
        for (ScorerId id : ScorerId.values()) {
            weights.put(id, BigDecimal.ZERO);
        }

        assertThatThrownBy(() -> build(weights))
                .isInstanceOf(RecommendationValidationException.class)
                .hasMessageContaining("at least one");
    }

    @Test
    void rejectsInvalidThresholdsAndPercentages() {
        assertThatThrownBy(() -> new RecommendationConfig(true, weights(),
                new BigDecimal("101"), BigDecimal.ONE, new BigDecimal("1.2"),
                new BigDecimal("0.8"), new BigDecimal("80"), new BigDecimal("20"),
                Set.of(ExpiryKind.WEEKLY), ExpirySelection.NEAREST, atm()))
                .isInstanceOf(RecommendationValidationException.class)
                .hasMessageContaining("minimumConfidence");

        assertThatThrownBy(() -> new RecommendationConfig(true, weights(),
                BigDecimal.ZERO, BigDecimal.ONE, new BigDecimal("0.8"),
                new BigDecimal("0.9"), new BigDecimal("80"), new BigDecimal("20"),
                Set.of(ExpiryKind.WEEKLY), ExpirySelection.NEAREST, atm()))
                .isInstanceOf(RecommendationValidationException.class)
                .hasMessageContaining("pcrBullishThreshold");

        assertThatThrownBy(() -> new RecommendationConfig(true, weights(),
                BigDecimal.ZERO, BigDecimal.ONE, new BigDecimal("1.2"),
                new BigDecimal("0.8"), new BigDecimal("20"), new BigDecimal("80"),
                Set.of(ExpiryKind.WEEKLY), ExpirySelection.NEAREST, atm()))
                .isInstanceOf(RecommendationValidationException.class)
                .hasMessageContaining("highIvRank");

        assertThatThrownBy(() -> new RecommendationConfig(true, weights(),
                BigDecimal.ZERO, BigDecimal.ONE, new BigDecimal("1.2"),
                new BigDecimal("0.8"), new BigDecimal("80"), new BigDecimal("20"),
                Set.of(), ExpirySelection.NEAREST, atm()))
                .isInstanceOf(RecommendationValidationException.class)
                .hasMessageContaining("allowedExpiryKinds");
    }

    @Test
    void rejectsInvalidStrikePolicies() {
        assertThatThrownBy(() -> new StrikeSelectionPolicy(StrikeMode.ATM, 1, 5))
                .isInstanceOf(RecommendationValidationException.class)
                .hasMessageContaining("ATM");
        assertThatThrownBy(() -> new StrikeSelectionPolicy(StrikeMode.OTM, 0, 5))
                .isInstanceOf(RecommendationValidationException.class)
                .hasMessageContaining("offsetSteps");
        assertThatThrownBy(() -> new StrikeSelectionPolicy(StrikeMode.OTM, 6, 5))
                .isInstanceOf(RecommendationValidationException.class)
                .hasMessageContaining("maximumSteps");
    }

    @Test
    void propertiesMapWeightsAndPolicies() {
        RecommendationProperties properties = new RecommendationProperties(true,
                Map.of("trend", BigDecimal.TEN), BigDecimal.ZERO, BigDecimal.ONE,
                new BigDecimal("1.2"), new BigDecimal("0.8"), new BigDecimal("80"),
                new BigDecimal("20"), List.of("WEEKLY", "MONTHLY"), "NEXT", "OTM",
                1, 5);

        assertThatThrownBy(properties::toConfig)
                .isInstanceOf(RecommendationValidationException.class)
                .hasMessageContaining("missing required scorer");
    }

    @Test
    void propertiesRejectUnknownAndDuplicateScorerNames() {
        RecommendationProperties unknown = new RecommendationProperties(true,
                Map.of("does_not_exist", BigDecimal.ONE), BigDecimal.ZERO,
                BigDecimal.ONE, new BigDecimal("1.2"), new BigDecimal("0.8"),
                new BigDecimal("80"), new BigDecimal("20"), List.of("WEEKLY"),
                "NEAREST", "ATM", 0, 5);
        assertThatThrownBy(unknown::toConfig)
                .isInstanceOf(RecommendationValidationException.class)
                .hasMessageContaining("unknown scorer");

        RecommendationProperties duplicate = new RecommendationProperties(true,
                Map.of("open-interest", BigDecimal.ONE, "open_interest",
                        BigDecimal.ONE), BigDecimal.ZERO, BigDecimal.ONE,
                new BigDecimal("1.2"), new BigDecimal("0.8"), new BigDecimal("80"),
                new BigDecimal("20"), List.of("WEEKLY"), "NEAREST", "ATM", 0, 5);
        assertThatThrownBy(duplicate::toConfig)
                .isInstanceOf(RecommendationValidationException.class)
                .hasMessageContaining("duplicate scorer");
    }

    @Test
    void propertiesConvertACompleteConfiguration() {
        Map<String, BigDecimal> weights = new java.util.LinkedHashMap<>();
        for (ScorerId id : ScorerId.values()) {
            weights.put(id.id(), BigDecimal.ONE);
        }
        RecommendationProperties properties = new RecommendationProperties(true,
                weights, new BigDecimal("55"), new BigDecimal("5"),
                new BigDecimal("1.2"), new BigDecimal("0.8"), new BigDecimal("80"),
                new BigDecimal("20"), List.of("WEEKLY", "MONTHLY"), "NEAREST",
                "ATM", 0, 5);

        RecommendationConfig config = properties.toConfig();

        assertThat(config.weights()).hasSize(ScorerId.values().length);
        assertThat(config.allowedExpiryKinds())
                .containsExactlyInAnyOrder(ExpiryKind.WEEKLY, ExpiryKind.MONTHLY);
        assertThat(config.strikeSelection().mode()).isEqualTo(StrikeMode.ATM);
        assertThat(config.minimumConfidence()).isEqualByComparingTo("55");
    }

    private static RecommendationConfig build(Map<ScorerId, BigDecimal> weights) {
        return new RecommendationConfig(true, weights, BigDecimal.ZERO,
                BigDecimal.ONE, new BigDecimal("1.2"), new BigDecimal("0.8"),
                new BigDecimal("80"), new BigDecimal("20"),
                Set.of(ExpiryKind.WEEKLY), ExpirySelection.NEAREST, atm());
    }

    private static StrikeSelectionPolicy atm() {
        return new StrikeSelectionPolicy(StrikeMode.ATM, 0, 5);
    }

    private static Map<ScorerId, BigDecimal> weights() {
        Map<ScorerId, BigDecimal> weights = new EnumMap<>(ScorerId.class);
        for (ScorerId id : ScorerId.values()) {
            weights.put(id, BigDecimal.ONE);
        }
        return weights;
    }
}

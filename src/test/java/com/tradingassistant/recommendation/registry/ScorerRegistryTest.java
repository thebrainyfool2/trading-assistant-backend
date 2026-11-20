package com.tradingassistant.recommendation.registry;

import com.tradingassistant.recommendation.RecommendationTestFixtures;
import com.tradingassistant.recommendation.domain.ScorerId;
import com.tradingassistant.recommendation.exception.RecommendationValidationException;
import com.tradingassistant.recommendation.scorer.TrendScorer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScorerRegistryTest {

    @Test
    void registersEveryRequiredScorerOnce() {
        ScorerRegistry registry = ScorerRegistry.standard(
                RecommendationTestFixtures.config());

        assertThat(registry.size()).isEqualTo(ScorerId.values().length);
        assertThat(registry.scorers()).hasSize(ScorerId.values().length);
    }

    @Test
    void rejectsDuplicateScorerIds() {
        List<com.tradingassistant.recommendation.api.RecommendationScorer> scorers =
                new ArrayList<>(ScorerRegistry.standard(
                        RecommendationTestFixtures.config()).scorers());
        scorers.add(new TrendScorer());

        assertThatThrownBy(() -> new ScorerRegistry(scorers))
                .isInstanceOf(RecommendationValidationException.class)
                .hasMessageContaining("duplicate scorer");
    }

    @Test
    void rejectsMissingScorers() {
        List<com.tradingassistant.recommendation.api.RecommendationScorer> scorers =
                new ArrayList<>(ScorerRegistry.standard(
                        RecommendationTestFixtures.config()).scorers());
        scorers.removeIf(scorer -> scorer.id() == ScorerId.FRESHNESS);

        assertThatThrownBy(() -> new ScorerRegistry(scorers))
                .isInstanceOf(RecommendationValidationException.class)
                .hasMessageContaining("missing required scorer");
    }
}

package com.tradingassistant.recommendation.domain;

import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.Segment;
import com.tradingassistant.recommendation.RecommendationTestFixtures;
import com.tradingassistant.recommendation.config.RecommendationConfig;
import com.tradingassistant.recommendation.engine.RecommendationEngine;
import com.tradingassistant.recommendation.registry.ScorerRegistry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationBatchTest {

    @Test
    void ranksByConfidenceThenWeightedScore() {
        RecommendationConfig config = RecommendationTestFixtures.config();
        RecommendationEngine engine = new RecommendationEngine(
                ScorerRegistry.standard(config), config);
        List<ComponentScore> components = engine.evaluate(
                RecommendationTestFixtures.bullishContext()).components();
        Recommendation first = recommendation("NIFTY", new BigDecimal("70"),
                new BigDecimal("70"), components);
        Recommendation second = recommendation("BANKNIFTY", new BigDecimal("90"),
                new BigDecimal("80"), components);
        Recommendation third = recommendation("RELIANCE", new BigDecimal("70"),
                new BigDecimal("75"), components);

        RecommendationBatch batch = new RecommendationBatch(
                List.of(first, second, third));

        assertThat(batch.ranked()).extracting(RankedRecommendation::recommendation)
                .containsExactly(second, third, first);
        assertThat(batch.ranked()).extracting(RankedRecommendation::rank)
                .containsExactly(1, 2, 3);
        assertThat(batch.top()).get()
                .extracting(RankedRecommendation::recommendation)
                .isEqualTo(second);
    }

    @Test
    void handlesAnEmptyBatch() {
        RecommendationBatch batch = new RecommendationBatch(List.of());

        assertThat(batch.recommendations()).isEmpty();
        assertThat(batch.ranked()).isEmpty();
        assertThat(batch.top()).isEmpty();
    }

    private static Recommendation recommendation(String token, BigDecimal score,
                                                  BigDecimal confidence,
                                                  List<ComponentScore> components) {
        return new Recommendation(RecommendationType.NO_TRADE,
                new InstrumentKey(Segment.NSE_CASH, token), Interval.ONE_MINUTE,
                RecommendationTestFixtures.START, score, confidence, components,
                Optional.empty(), "test recommendation", Map.of());
    }
}

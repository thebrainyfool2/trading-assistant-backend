package com.tradingassistant.recommendation.registry;

import com.tradingassistant.recommendation.api.RecommendationScorer;
import com.tradingassistant.recommendation.config.RecommendationConfig;
import com.tradingassistant.recommendation.domain.ScorerId;
import com.tradingassistant.recommendation.exception.RecommendationValidationException;
import com.tradingassistant.recommendation.scorer.BreakoutScorer;
import com.tradingassistant.recommendation.scorer.ExpectedMoveScorer;
import com.tradingassistant.recommendation.scorer.FreshnessScorer;
import com.tradingassistant.recommendation.scorer.GreeksScorer;
import com.tradingassistant.recommendation.scorer.IvRankScorer;
import com.tradingassistant.recommendation.scorer.IvScorer;
import com.tradingassistant.recommendation.scorer.LiquidityScorer;
import com.tradingassistant.recommendation.scorer.MarketSessionScorer;
import com.tradingassistant.recommendation.scorer.MarketStructureScorer;
import com.tradingassistant.recommendation.scorer.MomentumScorer;
import com.tradingassistant.recommendation.scorer.OiChangeScorer;
import com.tradingassistant.recommendation.scorer.OpenInterestScorer;
import com.tradingassistant.recommendation.scorer.PcrScorer;
import com.tradingassistant.recommendation.scorer.QualificationScorer;
import com.tradingassistant.recommendation.scorer.RiskRewardScorer;
import com.tradingassistant.recommendation.scorer.StrategyAgreementScorer;
import com.tradingassistant.recommendation.scorer.SupportResistanceScorer;
import com.tradingassistant.recommendation.scorer.TrendAlignmentScorer;
import com.tradingassistant.recommendation.scorer.TrendScorer;
import com.tradingassistant.recommendation.scorer.VolatilityScorer;
import com.tradingassistant.recommendation.scorer.VolumeScorer;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable registry of all required scorers. Duplicate identities and missing
 * scorer types are rejected at construction.
 */
public final class ScorerRegistry {

    private final Map<ScorerId, RecommendationScorer> scorers;

    public ScorerRegistry(List<RecommendationScorer> scorers) {
        Objects.requireNonNull(scorers, "scorers must not be null");
        Map<ScorerId, RecommendationScorer> checked = new EnumMap<>(ScorerId.class);
        for (RecommendationScorer scorer : scorers) {
            Objects.requireNonNull(scorer, "scorer must not be null");
            if (checked.putIfAbsent(scorer.id(), scorer) != null) {
                throw new RecommendationValidationException(
                        "duplicate scorer id: " + scorer.id().id());
            }
        }
        for (ScorerId id : ScorerId.values()) {
            if (!checked.containsKey(id)) {
                throw new RecommendationValidationException(
                        "missing required scorer: " + id.id());
            }
        }
        this.scorers = Map.copyOf(checked);
    }

    public static ScorerRegistry standard(RecommendationConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        List<RecommendationScorer> scorers = new ArrayList<>(List.of(
                new TrendScorer(),
                new MomentumScorer(),
                new VolatilityScorer(),
                new LiquidityScorer(),
                new VolumeScorer(),
                new OpenInterestScorer(),
                new OiChangeScorer(),
                new PcrScorer(config),
                new IvScorer(),
                new IvRankScorer(config),
                new GreeksScorer(),
                new ExpectedMoveScorer(),
                new SupportResistanceScorer(),
                new BreakoutScorer(),
                new MarketStructureScorer(),
                new TrendAlignmentScorer(),
                new RiskRewardScorer(),
                new QualificationScorer(),
                new StrategyAgreementScorer(),
                new MarketSessionScorer(),
                new FreshnessScorer()));
        return new ScorerRegistry(scorers);
    }

    public RecommendationScorer get(ScorerId id) {
        RecommendationScorer scorer = scorers.get(id);
        if (scorer == null) {
            throw new RecommendationValidationException(
                    "scorer is not registered: " + id.id());
        }
        return scorer;
    }

    public List<RecommendationScorer> scorers() {
        return List.of(ScorerId.values()).stream().map(this::get).toList();
    }

    public int size() {
        return scorers.size();
    }
}

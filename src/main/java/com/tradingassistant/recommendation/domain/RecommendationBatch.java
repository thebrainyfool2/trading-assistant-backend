package com.tradingassistant.recommendation.domain;

import com.tradingassistant.recommendation.exception.RecommendationValidationException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A set of instrument recommendations with deterministic internal ranking.
 * There is intentionally no endpoint or UI in Phase 14.
 */
public record RecommendationBatch(List<Recommendation> recommendations) {

    private static final Comparator<Recommendation> ORDER =
            Comparator.comparing(Recommendation::confidence).reversed()
                    .thenComparing(Recommendation::weightedScore,
                            Comparator.reverseOrder())
                    .thenComparing(recommendation -> recommendation.type().ordinal())
                    .thenComparing(recommendation ->
                            recommendation.instrument().segment().name())
                    .thenComparing(recommendation ->
                            recommendation.instrument().symbolToken());

    public RecommendationBatch {
        if (recommendations == null) {
            throw new RecommendationValidationException(
                    "recommendations must not be null");
        }
        recommendations = List.copyOf(recommendations);
        for (Recommendation recommendation : recommendations) {
            Objects.requireNonNull(recommendation, "recommendation must not be null");
        }
    }

    public List<RankedRecommendation> ranked() {
        List<Recommendation> sorted = new ArrayList<>(recommendations);
        sorted.sort(ORDER);
        AtomicInteger rank = new AtomicInteger(1);
        return sorted.stream()
                .map(recommendation -> new RankedRecommendation(
                        rank.getAndIncrement(), recommendation))
                .toList();
    }

    public Optional<RankedRecommendation> top() {
        return ranked().stream().findFirst();
    }

    public static RecommendationBatch of(Recommendation... values) {
        return new RecommendationBatch(List.of(values));
    }
}

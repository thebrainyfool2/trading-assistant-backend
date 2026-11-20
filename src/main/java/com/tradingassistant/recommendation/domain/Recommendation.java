package com.tradingassistant.recommendation.domain;

import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.OptionType;
import com.tradingassistant.recommendation.exception.RecommendationValidationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable, fully explainable recommendation for one instrument, interval and
 * market timestamp.
 */
public record Recommendation(
        RecommendationType type,
        InstrumentKey instrument,
        Interval interval,
        Instant timestamp,
        BigDecimal weightedScore,
        BigDecimal confidence,
        List<ComponentScore> components,
        Optional<OptionSelection> option,
        String summary,
        Map<String, String> diagnostics) {

    public Recommendation {
        type = Objects.requireNonNull(type, "type must not be null");
        instrument = Objects.requireNonNull(instrument, "instrument must not be null");
        interval = Objects.requireNonNull(interval, "interval must not be null");
        timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
        weightedScore = Objects.requireNonNull(weightedScore,
                "weightedScore must not be null");
        confidence = Objects.requireNonNull(confidence, "confidence must not be null");
        if (weightedScore.signum() < 0) {
            throw new RecommendationValidationException(
                    "weightedScore must not be negative");
        }
        if (confidence.signum() < 0
                || confidence.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new RecommendationValidationException(
                    "confidence must be between 0 and 100");
        }
        if (components == null || components.isEmpty()) {
            throw new RecommendationValidationException(
                    "components must not be empty");
        }
        List<ComponentScore> checked = List.copyOf(components);
        Set<ScorerId> seen = new HashSet<>();
        for (ComponentScore component : checked) {
            if (!seen.add(component.scorerId())) {
                throw new RecommendationValidationException(
                        "duplicate scorer component: " + component.scorerId().id());
            }
        }
        components = checked;
        option = option == null ? Optional.empty() : option;
        if (type == RecommendationType.NO_TRADE && option.isPresent()) {
            throw new RecommendationValidationException(
                    "NO_TRADE must not contain an option selection");
        }
        if (type == RecommendationType.BUY_CALL
                && !option.map(selection -> selection.optionType() == OptionType.CALL)
                .orElse(false)) {
            throw new RecommendationValidationException(
                    "BUY_CALL requires a call option selection");
        }
        if (type == RecommendationType.BUY_PUT
                && !option.map(selection -> selection.optionType() == OptionType.PUT)
                .orElse(false)) {
            throw new RecommendationValidationException(
                    "BUY_PUT requires a put option selection");
        }
        if (summary == null || summary.isBlank()) {
            throw new RecommendationValidationException("summary must not be blank");
        }
        summary = summary.trim();
        diagnostics = diagnostics == null ? Map.of() : Map.copyOf(diagnostics);
    }

    public Optional<ComponentScore> component(ScorerId scorerId) {
        return components.stream()
                .filter(component -> component.scorerId() == scorerId)
                .findFirst();
    }

    /**
     * Signed difference between weighted bullish and bearish contributions.
     * Positive values support a call and negative values support a put.
     */
    public BigDecimal directionalEdge() {
        BigDecimal bullish = components.stream()
                .filter(component -> component.bias() == DirectionalBias.BULLISH)
                .map(ComponentScore::weightedContribution)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal bearish = components.stream()
                .filter(component -> component.bias() == DirectionalBias.BEARISH)
                .map(ComponentScore::weightedContribution)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return bullish.subtract(bearish);
    }
}

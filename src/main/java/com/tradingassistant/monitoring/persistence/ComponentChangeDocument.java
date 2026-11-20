package com.tradingassistant.monitoring.persistence;

import com.tradingassistant.monitoring.domain.ComponentChange;
import com.tradingassistant.recommendation.domain.ScorerId;

import java.math.BigDecimal;

/** Embedded component delta stored with a monitoring result. */
public record ComponentChangeDocument(
        ScorerId scorerId,
        BigDecimal previousScore,
        BigDecimal currentScore,
        BigDecimal delta,
        String explanation) {

    public static ComponentChangeDocument from(ComponentChange change) {
        return new ComponentChangeDocument(change.scorerId(), change.previousScore(),
                change.currentScore(), change.delta(), change.explanation());
    }

    public ComponentChange toDomain() {
        return new ComponentChange(scorerId, previousScore, currentScore, delta,
                explanation);
    }
}
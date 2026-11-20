package com.tradingassistant.recommendation.event;

/**
 * Outbound port for recommendation events.
 */
@FunctionalInterface
public interface RecommendationEventPublisher {

    void publish(RecommendationEvent event);
}

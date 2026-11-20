package com.tradingassistant.recommendation.event;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Small synchronous bus for recommendation events. It is separate from the
 * sealed market event hierarchy and does not alter existing event contracts.
 */
public final class RecommendationEventBus implements RecommendationEventPublisher {

    private final CopyOnWriteArrayList<Consumer<RecommendationEvent>> subscribers =
            new CopyOnWriteArrayList<>();

    @Override
    public void publish(RecommendationEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        for (Consumer<RecommendationEvent> subscriber : subscribers) {
            subscriber.accept(event);
        }
    }

    /**
     * Registers a subscriber and returns a handle that removes it.
     */
    public AutoCloseable subscribe(Consumer<RecommendationEvent> subscriber) {
        Objects.requireNonNull(subscriber, "subscriber must not be null");
        subscribers.add(subscriber);
        return () -> subscribers.remove(subscriber);
    }

    public int subscriberCount() {
        return subscribers.size();
    }

    public List<Consumer<RecommendationEvent>> subscribers() {
        return List.copyOf(subscribers);
    }
}

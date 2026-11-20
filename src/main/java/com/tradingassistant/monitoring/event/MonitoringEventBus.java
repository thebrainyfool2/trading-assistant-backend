package com.tradingassistant.monitoring.event;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Small synchronous bus for monitoring events. */
public final class MonitoringEventBus implements MonitoringEventPublisher {

    private final CopyOnWriteArrayList<Consumer<MonitoringEvent>> subscribers =
            new CopyOnWriteArrayList<>();

    @Override
    public void publish(MonitoringEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        for (Consumer<MonitoringEvent> subscriber : subscribers) {
            subscriber.accept(event);
        }
    }

    public AutoCloseable subscribe(Consumer<MonitoringEvent> subscriber) {
        Objects.requireNonNull(subscriber, "subscriber must not be null");
        subscribers.add(subscriber);
        return () -> subscribers.remove(subscriber);
    }

    public int subscriberCount() {
        return subscribers.size();
    }

    public List<Consumer<MonitoringEvent>> subscribers() {
        return List.copyOf(subscribers);
    }
}
package com.tradingassistant.pipeline.event;

import com.tradingassistant.pipeline.exception.EventDeliveryException;
import com.tradingassistant.pipeline.exception.PipelineException;
import com.tradingassistant.pipeline.exception.PipelineValidationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-process synchronous event bus.
 *
 * <p>Delivery policy: every active subscriber receives the event in subscription
 * order, even when an earlier subscriber fails. After delivery, the first failure
 * is rethrown as an {@link EventDeliveryException} with the remaining failures
 * attached as suppressed exceptions, so a broken consumer is visible and never
 * silently dropped. Subscribing or publishing after {@link #close()} is a
 * programming error.</p>
 */
public final class MarketEventBus implements MarketEventPublisher, AutoCloseable {

    private final CopyOnWriteArrayList<Subscription> subscriptions = new CopyOnWriteArrayList<>();
    private volatile boolean closed;

    public Subscription subscribe(MarketEventListener listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        if (closed) {
            throw new PipelineException("The market event bus is closed");
        }
        Subscription subscription = new Subscription(this, listener);
        subscriptions.add(subscription);
        return subscription;
    }

    @Override
    public void publish(MarketEvent event) {
        if (event == null) {
            throw new PipelineValidationException("event must not be null");
        }
        if (closed) {
            throw new PipelineException("The market event bus is closed");
        }
        List<Throwable> failures = null;
        for (Subscription subscription : subscriptions) {
            if (!subscription.isActive()) {
                continue;
            }
            try {
                subscription.listener().onEvent(event);
            } catch (RuntimeException failure) {
                if (failures == null) {
                    failures = new ArrayList<>(2);
                }
                failures.add(failure);
            }
        }
        if (failures != null) {
            EventDeliveryException exception = new EventDeliveryException(
                    "Event delivery failed for " + event.type()
                            + " with " + failures.size() + " listener failure(s)",
                    failures.getFirst());
            for (int i = 1; i < failures.size(); i++) {
                exception.addSuppressed(failures.get(i));
            }
            throw exception;
        }
    }

    public int subscriberCount() {
        int active = 0;
        for (Subscription subscription : subscriptions) {
            if (subscription.isActive()) {
                active++;
            }
        }
        return active;
    }

    @Override
    public void close() {
        closed = true;
        for (Subscription subscription : subscriptions) {
            subscription.cancel();
        }
        subscriptions.clear();
    }

    /**
     * Handle for one subscriber. Closing the handle unsubscribes it; closing it
     * twice is harmless.
     */
    public static final class Subscription implements AutoCloseable {

        private final MarketEventBus owner;
        private final MarketEventListener listener;
        private volatile boolean active = true;

        private Subscription(MarketEventBus owner, MarketEventListener listener) {
            this.owner = owner;
            this.listener = listener;
        }

        MarketEventListener listener() {
            return listener;
        }

        public boolean isActive() {
            return active;
        }

        public void cancel() {
            if (!active) {
                return;
            }
            active = false;
            owner.subscriptions.remove(this);
        }

        @Override
        public void close() {
            cancel();
        }
    }
}

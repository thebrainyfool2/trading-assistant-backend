package com.tradingassistant.platform.config;

import com.tradingassistant.pipeline.event.MarketEventBus;
import com.tradingassistant.pipeline.event.MarketEventListener;
import com.tradingassistant.platform.history.ExecutionHistoryListener;
import com.tradingassistant.platform.notification.ExecutionNotificationListener;
import com.tradingassistant.platform.readmodel.PipelineSnapshotStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.util.List;

/**
 * Wires the application platform around the trading engine without changing it.
 *
 * <p>The platform owns a clock for its own timestamps and a bridge that connects
 * read-side projections to the engine's event bus. The bus exists only when the
 * broker is enabled, so the bridge starts and stops with it and the REST API
 * keeps starting in an empty configuration.</p>
 */
@Configuration(proxyBeanMethods = false)
public class PlatformConfiguration {

    @Bean
    @Primary
    public Clock platformClock() {
        return Clock.systemUTC();
    }

    @Bean
    public PlatformEventBridge platformEventBridge(
            ObjectProvider<MarketEventBus> busProvider,
            PipelineSnapshotStore snapshotStore,
            ExecutionHistoryListener historyListener,
            ExecutionNotificationListener notificationListener) {
        return new PlatformEventBridge(busProvider,
                List.of(snapshotStore, historyListener, notificationListener));
    }

    /**
     * Subscribes the platform projections to the engine bus when one is present.
     *
     * <p>Each projection is an independent listener. A failure in one projection
     * must not stop the engine or the other projections, so failures are logged
     * and swallowed here: the engine's own publication contract is unaffected.</p>
     */
    public static final class PlatformEventBridge implements SmartLifecycle {

        private static final Logger log = LoggerFactory.getLogger(PlatformEventBridge.class);

        private final ObjectProvider<MarketEventBus> busProvider;
        private final List<MarketEventListener> listeners;
        private volatile MarketEventBus.Subscription subscription;
        private volatile boolean running;

        PlatformEventBridge(ObjectProvider<MarketEventBus> busProvider,
                            List<MarketEventListener> listeners) {
            this.busProvider = busProvider;
            this.listeners = List.copyOf(listeners);
        }

        @Override
        public void start() {
            MarketEventBus bus = busProvider.getIfAvailable();
            if (bus == null) {
                log.debug("No engine event bus is configured; platform projections stay idle");
                running = true;
                return;
            }
            subscription = bus.subscribe(this::deliver);
            running = true;
            log.info("Platform projections subscribed to the engine event bus");
        }

        @Override
        public void stop() {
            MarketEventBus.Subscription current = subscription;
            subscription = null;
            if (current != null) {
                current.cancel();
            }
            running = false;
        }

        @Override
        public boolean isRunning() {
            return running;
        }

        private void deliver(com.tradingassistant.pipeline.event.MarketEvent event) {
            for (MarketEventListener listener : listeners) {
                try {
                    listener.onEvent(event);
                } catch (RuntimeException failure) {
                    log.warn("Platform projection {} failed for event {}: {}",
                            listener, event.type(), failure.toString());
                }
            }
        }
    }
}

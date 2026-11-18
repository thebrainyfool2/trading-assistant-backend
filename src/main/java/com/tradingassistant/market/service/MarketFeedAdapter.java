package com.tradingassistant.market.service;

import com.tradingassistant.broker.api.MarketFeedConnection;
import com.tradingassistant.market.domain.Tick;
import com.tradingassistant.market.exception.MarketDataException;
import com.tradingassistant.market.exception.MarketDataUnavailableException;
import com.tradingassistant.market.mapping.TickMapper;
import com.tradingassistant.market.time.MarketStatusResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hides the provider feed and translates every tick at the boundary. A mapping
 * failure is delivered to the application listener instead of escaping into the
 * provider's callback thread.
 */
final class MarketFeedAdapter implements MarketFeed {

    private static final Logger log = LoggerFactory.getLogger(MarketFeedAdapter.class);

    private final MarketFeedConnection connection;

    private MarketFeedAdapter(MarketFeedConnection connection) {
        this.connection = connection;
    }

    static MarketFeedAdapter open(com.tradingassistant.broker.api.BrokerProvider broker,
                                  com.tradingassistant.broker.model.FeedSubscription subscription,
                                  MarketFeedListener listener,
                                  TickMapper tickMapper,
                                  MarketStatusResolver marketTime) {
        Bridge bridge = new Bridge(listener, tickMapper, marketTime);
        MarketFeedConnection connection = broker.openMarketFeed(subscription, bridge);
        return new MarketFeedAdapter(connection);
    }

    @Override
    public boolean isOpen() {
        return connection.isOpen();
    }

    @Override
    public void close() {
        connection.close();
    }

    private static final class Bridge implements com.tradingassistant.broker.api.MarketFeedListener {

        private final MarketFeedListener listener;
        private final TickMapper tickMapper;
        private final MarketStatusResolver marketTime;

        private Bridge(MarketFeedListener listener,
                       TickMapper tickMapper,
                       MarketStatusResolver marketTime) {
            this.listener = listener;
            this.tickMapper = tickMapper;
            this.marketTime = marketTime;
        }

        @Override
        public void onConnected() {
            safely(listener::onConnected, "connected");
        }

        @Override
        public void onTick(com.tradingassistant.broker.model.FeedTick tick) {
            try {
                java.time.Instant receivedAt = marketTime.now();
                Tick domainTick = tickMapper.toDomain(
                        tick,
                        receivedAt,
                        marketTime.resolve(receivedAt));
                safely(() -> listener.onTick(domainTick), "tick");
            } catch (MarketDataException exception) {
                safely(() -> listener.onError(exception), "mapping error");
            }
        }

        @Override
        public void onDisconnected(int statusCode, String reason) {
            safely(() -> listener.onDisconnected(statusCode, reason), "disconnected");
        }

        @Override
        public void onError(Throwable error) {
            Throwable domainError = error instanceof MarketDataException
                    ? error
                    : new MarketDataUnavailableException("The market feed failed.", error);
            safely(() -> listener.onError(domainError), "provider error");
        }

        private void safely(Runnable action, String event) {
            try {
                action.run();
            } catch (RuntimeException exception) {
                log.warn("market_feed_listener_failed event={} errorType={}",
                        event, exception.getClass().getSimpleName());
            }
        }
    }
}

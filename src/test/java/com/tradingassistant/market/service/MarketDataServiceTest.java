package com.tradingassistant.market.service;

import com.tradingassistant.broker.api.BrokerProvider;
import com.tradingassistant.broker.api.MarketFeedConnection;
import com.tradingassistant.broker.model.FeedSubscription;
import com.tradingassistant.market.MarketTestFixtures;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.MarketStatus;
import com.tradingassistant.market.domain.Tick;
import com.tradingassistant.market.domain.TickMode;
import com.tradingassistant.market.exception.MarketDataUnavailableException;
import com.tradingassistant.market.mapping.CandleMapper;
import com.tradingassistant.market.mapping.InstrumentMapper;
import com.tradingassistant.market.mapping.MarketDepthMapper;
import com.tradingassistant.market.mapping.OptionContractMapper;
import com.tradingassistant.market.mapping.QuoteMapper;
import com.tradingassistant.market.mapping.TickMapper;
import com.tradingassistant.market.time.MarketStatusResolver;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketDataServiceTest {

    private final BrokerProvider broker = mock(BrokerProvider.class);
    private final MarketFeedConnection connection = mock(MarketFeedConnection.class);
    private final MarketStatusResolver marketTime = new MarketStatusResolver(
            Clock.fixed(MarketTestFixtures.MARKET_OPEN, ZoneOffset.UTC));
    private final MarketDepthMapper depthMapper = new MarketDepthMapper();
    private final MarketDataService service = new MarketDataService(
            broker,
            new InstrumentService(broker, new InstrumentMapper(), new OptionContractMapper()),
            new QuoteService(broker, new QuoteMapper(depthMapper), marketTime),
            new HistoricalDataService(broker, new CandleMapper(), marketTime),
            new TickMapper(depthMapper),
            marketTime);

    @Test
    void translatesProviderFeedCallbacksIntoDomainTicks() {
        when(broker.openMarketFeed(any(), any())).thenReturn(connection);
        when(connection.isOpen()).thenReturn(true);
        RecordingListener listener = new RecordingListener();

        MarketFeed feed = service.openFeed(List.of(MarketTestFixtures.KEY), TickMode.SNAPSHOT, listener);

        ArgumentCaptor<FeedSubscription> subscription =
                ArgumentCaptor.forClass(FeedSubscription.class);
        ArgumentCaptor<com.tradingassistant.broker.api.MarketFeedListener> brokerListener =
                ArgumentCaptor.forClass(com.tradingassistant.broker.api.MarketFeedListener.class);
        verify(broker).openMarketFeed(subscription.capture(), brokerListener.capture());
        assertThat(subscription.getValue().mode()).isEqualTo(
                com.tradingassistant.broker.model.FeedMode.SNAP_QUOTE);
        assertThat(subscription.getValue().tokens()).containsEntry(
                com.tradingassistant.broker.model.Exchange.NSE, List.of("3045"));

        brokerListener.getValue().onConnected();
        brokerListener.getValue().onTick(MarketTestFixtures.providerTick());
        brokerListener.getValue().onDisconnected(1000, "closed");
        brokerListener.getValue().onError(new IllegalStateException("provider"));

        assertThat(listener.connected).isTrue();
        assertThat(listener.tick).isNotNull();
        assertThat(listener.tick.mode()).isEqualTo(TickMode.SNAPSHOT);
        assertThat(listener.tick.receivedAt()).isEqualTo(MarketTestFixtures.MARKET_OPEN);
        assertThat(listener.tick.marketStatus()).isEqualTo(MarketStatus.OPEN);
        assertThat(listener.disconnected).isEqualTo(1000);
        assertThat(listener.error).isInstanceOf(MarketDataUnavailableException.class);
        assertThat(feed.isOpen()).isTrue();

        feed.close();
        verify(connection).close();
    }

    @Test
    void rejectsEmptyAndUnsupportedSubscriptions() {
        assertThatThrownBy(() -> service.openFeed(List.of(), TickMode.LTP, new RecordingListener()))
                .isInstanceOf(com.tradingassistant.market.exception.MarketDataValidationException.class);

        InstrumentKey cds = new InstrumentKey(
                com.tradingassistant.market.domain.Segment.CDS, "1");
        assertThatThrownBy(() -> service.openFeed(List.of(cds), TickMode.LTP, new RecordingListener()))
                .isInstanceOf(com.tradingassistant.market.exception.MarketDataValidationException.class)
                .hasMessageContaining("subscription");
    }

    private static final class RecordingListener implements MarketFeedListener {
        private boolean connected;
        private Tick tick;
        private int disconnected;
        private Throwable error;

        @Override
        public void onConnected() {
            connected = true;
        }

        @Override
        public void onTick(Tick tick) {
            this.tick = tick;
        }

        @Override
        public void onDisconnected(int statusCode, String reason) {
            this.disconnected = statusCode;
        }

        @Override
        public void onError(Throwable error) {
            this.error = error;
        }
    }
}

package com.tradingassistant.platform.live;

import com.tradingassistant.intelligence.service.MarketIntelligenceService;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.Segment;
import com.tradingassistant.market.domain.TickMode;
import com.tradingassistant.market.service.InstrumentService;
import com.tradingassistant.market.service.MarketDataService;
import com.tradingassistant.market.service.MarketFeed;
import com.tradingassistant.options.service.OptionsAnalyticsService;
import com.tradingassistant.pipeline.MarketProcessingPipeline;
import com.tradingassistant.pipeline.MarketSessionLifecycleManager;
import com.tradingassistant.pipeline.PipelineSubscription;
import com.tradingassistant.pipeline.calendar.MarketCalendarRegistry;
import com.tradingassistant.pipeline.session.IndicatorSessionKey;
import com.tradingassistant.platform.security.LocalPrincipalFilter;
import com.tradingassistant.platform.watchlist.WatchlistDocument;
import com.tradingassistant.platform.watchlist.WatchlistItem;
import com.tradingassistant.platform.watchlist.WatchlistRepository;
import com.tradingassistant.strategy.service.InMemoryStrategyMarketDataProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveMarketRunnerTest {

    private static final Instant NOW = Instant.parse("2026-11-19T10:00:00Z");
    private final MarketDataService marketData = mock(MarketDataService.class);
    private final MarketProcessingPipeline pipeline = mock(MarketProcessingPipeline.class);
    private final MarketSessionLifecycleManager lifecycle = mock(MarketSessionLifecycleManager.class);
    private final WatchlistRepository watchlists = mock(WatchlistRepository.class);
    private final InMemoryStrategyMarketDataProvider strategyData =
            mock(InMemoryStrategyMarketDataProvider.class);
    private final MarketIntelligenceService intelligence = mock(MarketIntelligenceService.class);
    private final OptionsAnalyticsService options = mock(OptionsAnalyticsService.class);
    private final LiveOptionChainService optionChain = mock(LiveOptionChainService.class);
    private final MarketFeed feed = mock(MarketFeed.class);
    private final LiveMarketRunner runner = runner();

    @Test
    void subscribesToConfiguredAndWatchlistInstrumentsOnFirstReconcile() {
        WatchlistDocument list = watchlist("1234");
        when(watchlists.findByUserIdOrderByCreatedAtAsc(LocalPrincipalFilter.OWNER_ID))
                .thenReturn(List.of(list));
        when(marketData.candles(any(), any(), any(), any())).thenReturn(List.of());
        when(marketData.openFeed(anyList(), eq(TickMode.QUOTE), any())).thenReturn(feed);
        when(feed.isOpen()).thenReturn(true);

        runner.reconcile();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<InstrumentKey>> instruments = ArgumentCaptor.forClass(List.class);
        verify(marketData).openFeed(instruments.capture(), eq(TickMode.QUOTE), any());
        assertThat(instruments.getValue()).containsExactlyInAnyOrder(
                new InstrumentKey(Segment.NSE_CASH, "2885"),
                new InstrumentKey(Segment.NSE_FO, "1234"));
        verify(pipeline, times(2)).subscribe(any(PipelineSubscription.class));
        verify(lifecycle).start();
        assertThat(runner.monitoredInstruments()).containsExactlyInAnyOrder(
                new InstrumentKey(Segment.NSE_CASH, "2885"),
                new InstrumentKey(Segment.NSE_FO, "1234"));
    }

    @Test
    void removesPipelineAndEngineStateWhenWatchlistDropsAnInstrument() {
        when(marketData.candles(any(), any(), any(), any())).thenReturn(List.of());
        when(marketData.openFeed(anyList(), eq(TickMode.QUOTE), any())).thenReturn(feed);
        when(feed.isOpen()).thenReturn(true);
        when(watchlists.findByUserIdOrderByCreatedAtAsc(LocalPrincipalFilter.OWNER_ID))
                .thenReturn(List.of(watchlist("1234")));
        runner.reconcile();

        when(watchlists.findByUserIdOrderByCreatedAtAsc(LocalPrincipalFilter.OWNER_ID))
                .thenReturn(List.of());
        runner.reconcile();

        InstrumentKey removed = new InstrumentKey(Segment.NSE_FO, "1234");
        verify(pipeline).unsubscribe(removed);
        verify(lifecycle).closeSession(any(IndicatorSessionKey.class));
        verify(strategyData).remove(eq(removed), any());
        verify(intelligence).remove(any());
        verify(options).remove(removed);
        assertThat(runner.monitoredInstruments()).containsExactlyInAnyOrder(
                new InstrumentKey(Segment.NSE_CASH, "2885"));
    }

    private LiveMarketRunner runner() {
        return new LiveMarketRunner(marketData, pipeline, lifecycle, watchlists,
                MarketCalendarRegistry.defaults(), strategyData, intelligence, options,
                optionChain, mock(InstrumentService.class),
                Clock.fixed(NOW, ZoneOffset.UTC), true, true,
                "NSE_CASH:2885", "FIVE_MINUTE", 1, 10, Duration.ofMinutes(10));
    }

    private static WatchlistDocument watchlist(String token) {
        return new WatchlistDocument("list-1", LocalPrincipalFilter.OWNER_ID, "Main",
                List.of(new WatchlistItem("item-1", token, "SYMBOL" + token,
                        Segment.NSE_FO, NOW)), NOW, NOW);
    }
}

package com.tradingassistant.platform.live;

import com.tradingassistant.indicator.config.IndicatorConfig;
import com.tradingassistant.intelligence.service.MarketIntelligenceService;
import com.tradingassistant.intelligence.session.AnalysisKey;
import com.tradingassistant.market.domain.Candle;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.market.domain.Tick;
import com.tradingassistant.market.domain.TickMode;
import com.tradingassistant.market.service.InstrumentService;
import com.tradingassistant.market.service.MarketDataService;
import com.tradingassistant.market.service.MarketFeed;
import com.tradingassistant.market.service.MarketFeedListener;
import com.tradingassistant.options.service.OptionsAnalyticsService;
import com.tradingassistant.pipeline.MarketProcessingPipeline;
import com.tradingassistant.pipeline.MarketSessionLifecycleManager;
import com.tradingassistant.pipeline.PipelineSubscription;
import com.tradingassistant.pipeline.calendar.MarketCalendarRegistry;
import com.tradingassistant.platform.watchlist.WatchlistChangedEvent;
import com.tradingassistant.platform.watchlist.WatchlistDocument;
import com.tradingassistant.platform.watchlist.WatchlistRepository;
import com.tradingassistant.platform.security.LocalPrincipalFilter;
import com.tradingassistant.strategy.service.InMemoryStrategyMarketDataProvider;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Supervises the read-only live market stream.
 *
 * <p>The subscription set is not static. It is the configured baseline plus every
 * instrument in the current watchlists. A watchlist mutation schedules a
 * reconciliation; a periodic reconciliation is also a safety net for changes
 * made while the process was unavailable. Added instruments are subscribed and
 * seeded with history; removed instruments release their pipeline, strategy,
 * intelligence and options state.</p>
 *
 * <p>The coordinator never submits an order or touches a funds endpoint. It only
 * reads candles and ticks and feeds the deterministic pipeline.</p>
 */
@Component
@ConditionalOnProperty(prefix = "app.broker.angel-one", name = "enabled", havingValue = "true")
public class LiveMarketRunner {

    private static final Logger log = LoggerFactory.getLogger(LiveMarketRunner.class);

    private final MarketDataService marketData;
    private final MarketProcessingPipeline pipeline;
    private final MarketSessionLifecycleManager lifecycle;
    private final WatchlistRepository watchlists;
    private final MarketCalendarRegistry calendars;
    private final InMemoryStrategyMarketDataProvider strategyData;
    private final MarketIntelligenceService intelligence;
    private final OptionsAnalyticsService options;
    private final LiveOptionChainService optionChain;
    private final LiveSubscriptionPlanner planner;
    private final Clock clock;
    private final boolean enabled;
    private final boolean includeWatchlists;
    private final String configuredInstruments;
    private final Interval interval;
    private final int historyDays;

    private final ExecutorService bootstrap = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "smartapi-live-supervisor");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean reconcilePending = new AtomicBoolean(false);
    private final AtomicLong feedGeneration = new AtomicLong();
    private final Set<String> loggedWarnings = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Object lifecycleLock = new Object();
    private final LinkedHashSet<InstrumentKey> seeded = new LinkedHashSet<>();

    private volatile MarketFeed feed;
    private volatile Set<InstrumentKey> currentInstruments = Set.of();

    public LiveMarketRunner(
            MarketDataService marketData,
            MarketProcessingPipeline pipeline,
            MarketSessionLifecycleManager lifecycle,
            WatchlistRepository watchlists,
            MarketCalendarRegistry calendars,
            InMemoryStrategyMarketDataProvider strategyData,
            MarketIntelligenceService intelligence,
            OptionsAnalyticsService options,
            LiveOptionChainService optionChain,
            InstrumentService instruments,
            Clock clock,
            @Value("${app.live.enabled:true}") boolean enabled,
            @Value("${app.live.include-watchlists:true}") boolean includeWatchlists,
            @Value("${app.live.instruments:NSE_CASH:2885,NSE_CASH:NIFTY,NSE_CASH:BANKNIFTY,NSE_FO:NIFTY}")
            String configuredInstruments,
            @Value("${app.live.interval:FIVE_MINUTE}") String interval,
            @Value("${app.live.history-days:10}") int historyDays,
            @Value("${app.live.max-instruments:200}") int maxInstruments,
            @Value("${app.live.alias-cache-ttl:10m}") Duration aliasCacheTtl) {
        this.marketData = marketData;
        this.pipeline = pipeline;
        this.lifecycle = lifecycle;
        this.watchlists = watchlists;
        this.calendars = calendars;
        this.strategyData = strategyData;
        this.intelligence = intelligence;
        this.options = options;
        this.optionChain = optionChain;
        this.clock = clock;
        this.enabled = enabled;
        this.includeWatchlists = includeWatchlists;
        this.configuredInstruments = configuredInstruments;
        this.interval = Interval.valueOf(interval.trim().toUpperCase());
        this.historyDays = Math.max(1, historyDays);
        this.planner = new LiveSubscriptionPlanner(
                new LiveInstrumentResolver(instruments, clock, aliasCacheTtl), maxInstruments);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!enabled) {
            log.info("Live market supervisor is disabled by configuration");
            return;
        }
        submitReconcile();
    }

    @EventListener(WatchlistChangedEvent.class)
    public void onWatchlistChanged(WatchlistChangedEvent event) {
        if (enabled && event != null) {
            submitReconcile();
        }
    }

    @Scheduled(fixedDelayString = "${app.live.reconcile-interval-ms:15000}",
            initialDelayString = "${app.live.reconcile-initial-delay-ms:1000}")
    public void scheduledReconcile() {
        if (enabled) {
            submitReconcile();
        }
    }

    /**
     * Reconciles the feed immediately. Exposed for tests and operational tools.
     */
    public void reconcile() {
        if (!enabled) {
            return;
        }
        synchronized (lifecycleLock) {
            LiveSubscriptionPlanner.Plan plan = plan();
            plan.warnings().forEach(this::logWarningOnce);
            LinkedHashSet<InstrumentKey> desired = new LinkedHashSet<>(plan.instruments());
            Set<InstrumentKey> current = currentInstruments;

            List<InstrumentKey> added = desired.stream()
                    .filter(instrument -> !current.contains(instrument))
                    .toList();
            List<InstrumentKey> removed = current.stream()
                    .filter(instrument -> !desired.contains(instrument))
                    .toList();

            for (InstrumentKey instrument : removed) {
                removeInstrument(instrument);
            }
            for (InstrumentKey instrument : added) {
                pipeline.subscribe(subscription(instrument));
            }
            for (InstrumentKey instrument : desired) {
                if (!seeded.contains(instrument)) {
                    seedHistory(subscription(instrument));
                }
            }

            boolean reopen = !added.isEmpty() || !removed.isEmpty()
                    || feed == null || !feed.isOpen();
            if (reopen) {
                closeFeed();
                if (desired.isEmpty()) {
                    lifecycle.shutdown();
                    currentInstruments = Set.of();
                    log.info("Live market supervisor has no instruments to monitor");
                    return;
                }
                lifecycle.start();
                openFeed(List.copyOf(desired));
            }
            currentInstruments = Set.copyOf(desired);
            optionChain.refresh(currentInstruments);
            logCalendarState(desired);
        }
    }

    public Set<InstrumentKey> monitoredInstruments() {
        return Set.copyOf(currentInstruments);
    }

    private LiveSubscriptionPlanner.Plan plan() {
        List<WatchlistDocument> lists = List.of();
        if (includeWatchlists) {
            try {
                lists = watchlists.findByUserIdOrderByCreatedAtAsc(
                        LocalPrincipalFilter.OWNER_ID);
            } catch (RuntimeException failure) {
                log.warn("Could not load watchlists for live subscriptions: {}",
                        failure.toString());
            }
        }
        return planner.plan(configuredInstruments, lists);
    }

    private PipelineSubscription subscription(InstrumentKey instrument) {
        return PipelineSubscription.of(instrument, interval, IndicatorConfig.defaults(),
                LiveIndicators.asArray());
    }

    private void openFeed(List<InstrumentKey> instruments) {
        long generation = feedGeneration.incrementAndGet();
        try {
            MarketFeed opened = marketData.openFeed(instruments, TickMode.QUOTE,
                    listener(generation));
            if (generation != feedGeneration.get()) {
                opened.close();
                return;
            }
            feed = opened;
            log.info("SmartAPI feed opened for {} monitored instrument(s): {}",
                    instruments.size(), instruments);
        } catch (RuntimeException failure) {
            log.warn("Could not open the SmartAPI live feed: {}", failure.getMessage());
        }
    }

    private MarketFeedListener listener(long generation) {
        return new MarketFeedListener() {
            @Override
            public void onConnected() {
                if (generation == feedGeneration.get()) {
                    log.info("SmartAPI feed connected for {} monitored instrument(s)",
                            currentInstruments.size());
                }
            }

            @Override
            public void onTick(Tick tick) {
                if (generation != feedGeneration.get()) {
                    return;
                }
                try {
                    pipeline.process(tick);
                } catch (RuntimeException failure) {
                    log.warn("Rejected live tick for {}: {}",
                            tick.instrument(), failure.getMessage());
                }
            }

            @Override
            public void onDisconnected(int statusCode, String reason) {
                if (generation == feedGeneration.get()) {
                    log.warn("SmartAPI feed disconnected: {} {}", statusCode, reason);
                    submitReconcile();
                }
            }

            @Override
            public void onError(Throwable error) {
                log.warn("SmartAPI feed error: {}", error.getMessage());
            }
        };
    }

    private void removeInstrument(InstrumentKey instrument) {
        pipeline.unsubscribe(instrument);
        lifecycle.closeSession(subscription(instrument).sessionKey());
        strategyData.remove(instrument, interval);
        intelligence.remove(new AnalysisKey(instrument, interval));
        options.remove(instrument);
        seeded.remove(instrument);
        log.info("Stopped monitoring {}", instrument);
    }

    private void seedHistory(PipelineSubscription subscription) {
        if (seeded.contains(subscription.instrument())) {
            return;
        }
        Instant to = clock.instant();
        Instant from = to.minus(historyDays, ChronoUnit.DAYS);
        try {
            List<Candle> candles = marketData.candles(subscription.instrument(),
                            subscription.interval(), from, to)
                    .stream()
                    .sorted(Comparator.comparing(Candle::timestamp))
                    .toList();
            for (Candle candle : candles) {
                pipeline.ingest(subscription, candle);
            }
            seeded.add(subscription.instrument());
            log.info("Seeded {} historical {} candles for {}", candles.size(),
                    subscription.interval(), subscription.instrument());
        } catch (RuntimeException failure) {
            log.warn("Could not seed historical candles for {}: {}",
                    subscription.instrument(), failure.getMessage());
        }
    }

    private void closeFeed() {
        feedGeneration.incrementAndGet();
        MarketFeed current = feed;
        feed = null;
        if (current != null) {
            try {
                current.close();
            } catch (RuntimeException failure) {
                log.warn("Could not close the SmartAPI live feed cleanly: {}",
                        failure.getMessage());
            }
        }
    }

    private void logCalendarState(Set<InstrumentKey> instruments) {
        for (InstrumentKey instrument : instruments) {
            calendars.find(instrument.exchange()).ifPresent(calendar -> {
                Instant now = clock.instant();
                boolean tradingTime = calendar.isTradingTime(now);
                log.debug("Calendar {} for {} is {} at {}", calendar.code(), instrument,
                        tradingTime ? "open" : "closed", now);
            });
        }
    }

    private void submitReconcile() {
        if (!enabled || !reconcilePending.compareAndSet(false, true)) {
            return;
        }
        bootstrap.submit(() -> {
            try {
                reconcile();
            } catch (RuntimeException failure) {
                log.warn("Live subscription reconciliation failed: {}", failure.toString());
            } finally {
                reconcilePending.set(false);
            }
        });
    }

    private void logWarningOnce(String warning) {
        if (warning != null && loggedWarnings.add(warning)) {
            log.warn("Live subscription: {}", warning);
        }
    }

    @PreDestroy
    public void stop() {
        closeFeed();
        lifecycle.shutdown();
        bootstrap.shutdownNow();
    }
}

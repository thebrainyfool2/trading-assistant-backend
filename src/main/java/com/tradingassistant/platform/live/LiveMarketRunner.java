package com.tradingassistant.platform.live;

import com.tradingassistant.indicator.config.IndicatorConfig;
import com.tradingassistant.market.domain.Candle;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.market.domain.Segment;
import com.tradingassistant.market.domain.Tick;
import com.tradingassistant.market.domain.TickMode;
import com.tradingassistant.market.service.MarketDataService;
import com.tradingassistant.market.service.MarketFeed;
import com.tradingassistant.market.service.MarketFeedListener;
import com.tradingassistant.pipeline.MarketProcessingPipeline;
import com.tradingassistant.pipeline.PipelineSubscription;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Starts the read-only SmartAPI market stream after the application is ready.
 * Historical candles warm the indicator/strategy state immediately; the feed
 * then advances the same pipeline candle by candle.
 *
 * <p>This runner never submits an order or talks to a funds endpoint. It only
 * reads candles and ticks for analysis.</p>
 */
@Component
@ConditionalOnProperty(prefix = "app.broker.angel-one", name = "enabled", havingValue = "true")
public class LiveMarketRunner {

    private static final Logger log = LoggerFactory.getLogger(LiveMarketRunner.class);
    private static final Set<String> INDICATORS = Set.of(
            "SMA", "EMA", "RSI", "MACD", "ATR", "VWAP", "SUPERTREND", "ADX");

    private final MarketDataService marketData;
    private final MarketProcessingPipeline pipeline;
    private final boolean enabled;
    private final String instruments;
    private final Interval interval;
    private final int historyDays;
    private final ExecutorService bootstrap = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "smartapi-live-bootstrap");
        thread.setDaemon(true);
        return thread;
    });

    private volatile MarketFeed feed;

    public LiveMarketRunner(
            MarketDataService marketData,
            MarketProcessingPipeline pipeline,
            @Value("${app.live.enabled:true}") boolean enabled,
            @Value("${app.live.instruments:NSE_CASH:2885}") String instruments,
            @Value("${app.live.interval:FIVE_MINUTE}") String interval,
            @Value("${app.live.history-days:10}") int historyDays) {
        this.marketData = marketData;
        this.pipeline = pipeline;
        this.enabled = enabled;
        this.instruments = instruments;
        this.interval = Interval.valueOf(interval.trim().toUpperCase());
        this.historyDays = Math.max(1, historyDays);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!enabled) {
            log.info("Live market runner is disabled by configuration");
            return;
        }
        bootstrap.submit(this::bootstrap);
    }

    private void bootstrap() {
        List<InstrumentKey> keys = parseInstruments(instruments);
        if (keys.isEmpty()) {
            log.warn("Live market runner has no instruments configured");
            return;
        }

        List<PipelineSubscription> subscriptions = new ArrayList<>();
        for (InstrumentKey key : keys) {
            PipelineSubscription subscription = PipelineSubscription.of(
                    key, interval, IndicatorConfig.defaults(), INDICATORS.toArray(String[]::new));
            pipeline.subscribe(subscription);
            subscriptions.add(subscription);
            seedHistory(subscription);
        }

        try {
            MarketFeed opened = marketData.openFeed(keys, TickMode.QUOTE, new MarketFeedListener() {
                @Override
                public void onConnected() {
                    log.info("SmartAPI live market feed connected for {} instrument(s)", keys.size());
                }

                @Override
                public void onTick(Tick tick) {
                    try {
                        pipeline.process(tick);
                    } catch (RuntimeException exception) {
                        log.warn("Rejected market tick while processing the live feed: {}",
                                exception.getMessage());
                    }
                }

                @Override
                public void onDisconnected(int statusCode, String reason) {
                    log.warn("SmartAPI live market feed disconnected: {} {}", statusCode, reason);
                }

                @Override
                public void onError(Throwable error) {
                    log.warn("SmartAPI live market feed error: {}", error.getMessage());
                }
            });
            feed = opened;
            log.info("SmartAPI live market feed opened for {}", keys);
        } catch (RuntimeException exception) {
            log.warn("Could not open the SmartAPI live market feed: {}", exception.getMessage());
        }
    }

    private void seedHistory(PipelineSubscription subscription) {
        Instant to = Instant.now();
        Instant from = to.minus(historyDays, ChronoUnit.DAYS);
        try {
            List<Candle> candles = marketData.candles(subscription.instrument(), subscription.interval(), from, to)
                    .stream()
                    .sorted(Comparator.comparing(Candle::timestamp))
                    .toList();
            for (Candle candle : candles) {
                pipeline.ingest(subscription, candle);
            }
            log.info("Seeded {} historical {} candles for {}", candles.size(),
                    subscription.interval(), subscription.instrument());
        } catch (RuntimeException exception) {
            log.warn("Could not seed historical candles for {}: {}",
                    subscription.instrument(), exception.getMessage());
        }
    }

    private static List<InstrumentKey> parseInstruments(String configured) {
        Set<InstrumentKey> keys = new LinkedHashSet<>();
        if (configured == null || configured.isBlank()) {
            return List.of();
        }
        for (String entry : configured.split(",")) {
            String value = entry.trim();
            if (value.isEmpty()) {
                continue;
            }
            int separator = value.indexOf(':');
            if (separator <= 0 || separator == value.length() - 1) {
                throw new IllegalArgumentException("Instrument must be SEGMENT:TOKEN: " + value);
            }
            keys.add(new InstrumentKey(
                    Segment.fromCode(value.substring(0, separator)),
                    value.substring(separator + 1)));
        }
        return List.copyOf(keys);
    }

    @PreDestroy
    public void stop() {
        MarketFeed current = feed;
        if (current != null) {
            current.close();
        }
        bootstrap.shutdownNow();
    }
}
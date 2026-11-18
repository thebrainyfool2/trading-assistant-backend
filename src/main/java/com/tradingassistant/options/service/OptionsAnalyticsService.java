package com.tradingassistant.options.service;

import com.tradingassistant.intelligence.domain.MarketAssessment;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.options.config.OptionsAnalyticsConfig;
import com.tradingassistant.options.domain.ChainSnapshot;
import com.tradingassistant.options.domain.ChainStateSnapshot;
import com.tradingassistant.options.domain.OptionsAnalysisContext;
import com.tradingassistant.options.domain.OptionsAssessment;
import com.tradingassistant.options.engine.OptionsAnalyticsEngine;
import com.tradingassistant.options.exception.OptionsValidationException;
import com.tradingassistant.options.volatility.ImpliedVolatilityHistory;
import com.tradingassistant.pipeline.event.MarketAssessmentGeneratedEvent;
import com.tradingassistant.pipeline.event.MarketEvent;
import com.tradingassistant.pipeline.event.MarketEventListener;
import com.tradingassistant.pipeline.event.MarketEventPublisher;
import com.tradingassistant.pipeline.event.OptionsAssessmentGeneratedEvent;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Turns market assessments into options assessments.
 *
 * <p>The service consumes {@link MarketAssessmentGeneratedEvent} only. For each
 * assessment it resolves a chain capture through the {@link OptionChainProvider}
 * and publishes {@link OptionsAssessmentGeneratedEvent}. Instruments without a
 * registered option chain are ignored, so the service can run beside the market
 * intelligence layer without knowing which instruments list options.</p>
 *
 * <p>State is bounded and per underlying: a volatility history ring and the
 * previous chain state used to describe change. Nothing is persisted and no
 * state is shared between underlyings, so one instrument's capture cannot affect
 * another's assessment.</p>
 */
public final class OptionsAnalyticsService implements MarketEventListener {

    private final OptionsAnalyticsEngine engine;
    private final OptionsAnalyticsConfig config;
    private final OptionChainProvider provider;
    private final MarketEventPublisher publisher;
    private final Clock clock;
    private final ConcurrentHashMap<InstrumentKey, UnderlyingState> states =
            new ConcurrentHashMap<>();

    public OptionsAnalyticsService(OptionsAnalyticsEngine engine,
                                   OptionsAnalyticsConfig config,
                                   OptionChainProvider provider,
                                   MarketEventPublisher publisher,
                                   Clock clock) {
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.provider = Objects.requireNonNull(provider, "provider must not be null");
        this.publisher = Objects.requireNonNull(publisher, "publisher must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public void onEvent(MarketEvent event) {
        if (event == null) {
            throw new OptionsValidationException("Event must not be null.");
        }
        if (!(event instanceof MarketAssessmentGeneratedEvent generated)) {
            return;
        }
        MarketAssessment market = generated.assessment();
        Optional<ChainSnapshot> snapshot = provider.snapshotFor(market.instrument(),
                market.timestamp());
        if (snapshot.isEmpty()) {
            return;
        }
        UnderlyingState state = states.computeIfAbsent(market.instrument(),
                key -> new UnderlyingState(
                        new ImpliedVolatilityHistory(config.ivHistoryWindow())));
        OptionsAssessment assessment;
        synchronized (state) {
            assessment = engine.analyze(
                    new OptionsAnalysisContext(snapshot.get(), market, state.previous),
                    state.history);
            state.previous = ChainStateSnapshot.of(snapshot.get());
        }
        publisher.publish(new OptionsAssessmentGeneratedEvent(assessment, clock.instant()));
    }

    public int stateCount() {
        return states.size();
    }

    public Set<InstrumentKey> instruments() {
        return Set.copyOf(states.keySet());
    }

    public Optional<ImpliedVolatilityHistory> volatilityHistory(InstrumentKey instrument) {
        if (instrument == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(states.get(instrument)).map(entry -> entry.history);
    }

    public Optional<ChainStateSnapshot> previousChain(InstrumentKey instrument) {
        if (instrument == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(states.get(instrument))
                .map(entry -> entry.previous)
                .filter(previous -> !previous.isEmpty());
    }

    /**
     * Drops one instrument's history. Used when an instrument is unsubscribed.
     */
    public boolean remove(InstrumentKey instrument) {
        return instrument != null && states.remove(instrument) != null;
    }

    public void clear() {
        states.clear();
    }

    /**
     * Applies a function to an instrument's state under its lock. Exposed for
     * recovery code that must reset a single underlying without touching others.
     */
    public void reset(InstrumentKey instrument,
                      Function<UnderlyingState, UnderlyingState> operation) {
        Objects.requireNonNull(operation, "operation must not be null");
        if (instrument == null) {
            return;
        }
        states.computeIfPresent(instrument, (key, state) -> operation.apply(state));
    }

    /**
     * Per-underlying state. Access must be synchronized on the instance.
     */
    public static final class UnderlyingState {

        private final ImpliedVolatilityHistory history;
        private ChainStateSnapshot previous = ChainStateSnapshot.empty();

        private UnderlyingState(ImpliedVolatilityHistory history) {
            this.history = history;
        }

        public ImpliedVolatilityHistory history() {
            return history;
        }

        public ChainStateSnapshot previous() {
            return previous;
        }
    }
}

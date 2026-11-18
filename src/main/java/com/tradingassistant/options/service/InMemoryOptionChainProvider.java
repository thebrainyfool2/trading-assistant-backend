package com.tradingassistant.options.service;

import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.OptionContract;
import com.tradingassistant.market.domain.Quote;
import com.tradingassistant.options.chain.OptionChainBuilder;
import com.tradingassistant.options.domain.ChainSnapshot;
import com.tradingassistant.options.exception.OptionsValidationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory staging of the option universe and its latest quotes.
 *
 * <p>This is feed staging, not storage: nothing is written to disk, the contract
 * list is replaced wholesale per underlying, and quotes are keyed by instrument
 * so a partial feed update is a normal state. A capture is built on demand, so
 * the provider never hands out mutable state.</p>
 */
public final class InMemoryOptionChainProvider implements OptionChainProvider {

    private final OptionChainBuilder builder;
    private final ConcurrentHashMap<InstrumentKey, String> underlyings = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<OptionContract>> contracts =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<InstrumentKey, Quote> quotes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<InstrumentKey, BigDecimal> spotPrices =
            new ConcurrentHashMap<>();

    public InMemoryOptionChainProvider(OptionChainBuilder builder) {
        this.builder = Objects.requireNonNull(builder, "builder must not be null");
    }

    /**
     * Maps a spot instrument to the option underlying symbol it prices.
     */
    public void registerUnderlying(InstrumentKey spotInstrument, String underlying) {
        Objects.requireNonNull(spotInstrument, "spotInstrument must not be null");
        if (underlying == null || underlying.isBlank()) {
            throw new OptionsValidationException("underlying must not be blank");
        }
        underlyings.put(spotInstrument, underlying.trim().toUpperCase(Locale.ROOT));
    }

    public void updateContracts(String underlying, List<OptionContract> contracts) {
        if (underlying == null || underlying.isBlank()) {
            throw new OptionsValidationException("underlying must not be blank");
        }
        Objects.requireNonNull(contracts, "contracts must not be null");
        this.contracts.put(underlying.trim().toUpperCase(Locale.ROOT), List.copyOf(contracts));
    }

    public void updateQuotes(Collection<Quote> quotes) {
        Objects.requireNonNull(quotes, "quotes must not be null");
        for (Quote quote : quotes) {
            if (quote != null) {
                this.quotes.put(quote.instrument(), quote);
            }
        }
    }

    public void updateSpot(InstrumentKey spotInstrument, BigDecimal price) {
        Objects.requireNonNull(spotInstrument, "spotInstrument must not be null");
        Objects.requireNonNull(price, "price must not be null");
        if (price.signum() <= 0) {
            throw new OptionsValidationException("spot price must be positive");
        }
        spotPrices.put(spotInstrument, price);
    }

    @Override
    public Optional<ChainSnapshot> snapshotFor(InstrumentKey spotInstrument, Instant capturedAt) {
        if (spotInstrument == null || capturedAt == null) {
            return Optional.empty();
        }
        String underlying = underlyings.get(spotInstrument);
        if (underlying == null) {
            return Optional.empty();
        }
        List<OptionContract> universe = contracts.get(underlying);
        BigDecimal spot = spotPrices.get(spotInstrument);
        if (universe == null || universe.isEmpty() || spot == null) {
            return Optional.empty();
        }
        return Optional.of(builder.build(spotInstrument, underlying, spot, capturedAt, universe,
                Map.copyOf(quotes)));
    }

    public int registeredUnderlyings() {
        return underlyings.size();
    }

    public void clear() {
        underlyings.clear();
        contracts.clear();
        quotes.clear();
        spotPrices.clear();
    }
}

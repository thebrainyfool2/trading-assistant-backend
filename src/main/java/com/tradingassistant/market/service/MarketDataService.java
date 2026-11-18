package com.tradingassistant.market.service;

import com.tradingassistant.broker.api.BrokerProvider;
import com.tradingassistant.broker.exception.BrokerException;
import com.tradingassistant.broker.model.FeedMode;
import com.tradingassistant.broker.model.FeedSubscription;
import com.tradingassistant.market.domain.Candle;
import com.tradingassistant.market.domain.Instrument;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.market.domain.OptionContract;
import com.tradingassistant.market.domain.Quote;
import com.tradingassistant.market.domain.QuoteMode;
import com.tradingassistant.market.domain.TickMode;
import com.tradingassistant.market.exception.MarketDataValidationException;
import com.tradingassistant.market.mapping.TickMapper;
import com.tradingassistant.market.time.MarketStatusResolver;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Application facade for all approved market-data operations. Every returned
 * value is a domain model; provider models and feed callbacks remain internal.
 */
public final class MarketDataService {

    private final BrokerProvider broker;
    private final InstrumentService instrumentService;
    private final QuoteService quoteService;
    private final HistoricalDataService historicalDataService;
    private final TickMapper tickMapper;
    private final MarketStatusResolver marketTime;

    public MarketDataService(BrokerProvider broker,
                             InstrumentService instrumentService,
                             QuoteService quoteService,
                             HistoricalDataService historicalDataService,
                             TickMapper tickMapper,
                             MarketStatusResolver marketTime) {
        this.broker = broker;
        this.instrumentService = instrumentService;
        this.quoteService = quoteService;
        this.historicalDataService = historicalDataService;
        this.tickMapper = tickMapper;
        this.marketTime = marketTime;
    }

    public List<Instrument> instruments() {
        return instrumentService.instruments();
    }

    public List<OptionContract> optionContracts() {
        return instrumentService.optionContracts();
    }

    public List<Quote> quotes(QuoteMode mode, List<InstrumentKey> instruments) {
        return quoteService.quotes(mode, instruments);
    }

    public List<Candle> candles(InstrumentKey instrument, Interval interval, Instant from, Instant to) {
        return historicalDataService.candles(instrument, interval, from, to);
    }

    public MarketFeed openFeed(List<InstrumentKey> instruments, TickMode mode, MarketFeedListener listener) {
        if (instruments == null || instruments.isEmpty()) {
            throw new MarketDataValidationException("at least one instrument is required");
        }
        if (mode == null) {
            throw new MarketDataValidationException("tick mode must not be null");
        }
        if (listener == null) {
            throw new MarketDataValidationException("listener must not be null");
        }

        Set<InstrumentKey> unique = new LinkedHashSet<>();
        for (InstrumentKey instrument : instruments) {
            if (instrument == null) {
                throw new MarketDataValidationException("instrument keys must not contain null");
            }
            unique.add(instrument);
        }

        Map<com.tradingassistant.broker.model.Exchange, List<String>> grouped = new LinkedHashMap<>();
        for (InstrumentKey instrument : unique) {
            grouped.computeIfAbsent(QuoteService.exchange(instrument.segment()), ignored -> new ArrayList<>())
                    .add(instrument.symbolToken());
        }

        try {
            FeedSubscription subscription = new FeedSubscription(providerMode(mode), grouped);
            return MarketFeedAdapter.open(broker, subscription, listener, tickMapper, marketTime);
        } catch (BrokerException exception) {
            throw MarketDataFailureMapper.translate(exception);
        } catch (IllegalArgumentException exception) {
            throw new MarketDataValidationException("The market-feed subscription is invalid.", exception);
        }
    }

    private static FeedMode providerMode(TickMode mode) {
        return switch (mode) {
            case LTP -> FeedMode.LTP;
            case QUOTE -> FeedMode.QUOTE;
            case SNAPSHOT -> FeedMode.SNAP_QUOTE;
        };
    }
}

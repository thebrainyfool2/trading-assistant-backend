package com.tradingassistant.market.service;

import com.tradingassistant.broker.api.BrokerProvider;
import com.tradingassistant.broker.model.Exchange;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.MarketStatus;
import com.tradingassistant.market.domain.Quote;
import com.tradingassistant.market.domain.QuoteMode;
import com.tradingassistant.market.domain.Segment;
import com.tradingassistant.market.exception.MarketDataValidationException;
import com.tradingassistant.market.mapping.QuoteMapper;
import com.tradingassistant.market.time.MarketStatusResolver;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Broker-independent quote access. The service groups domain instrument keys by
 * segment, applies the provider request limit and returns only domain quotes.
 */
public final class QuoteService {

    private static final int MAX_QUOTE_INSTRUMENTS = 50;

    private final BrokerProvider broker;
    private final QuoteMapper quoteMapper;
    private final MarketStatusResolver marketStatusResolver;

    public QuoteService(BrokerProvider broker,
                        QuoteMapper quoteMapper,
                        MarketStatusResolver marketStatusResolver) {
        this.broker = broker;
        this.quoteMapper = quoteMapper;
        this.marketStatusResolver = marketStatusResolver;
    }

    public List<Quote> quotes(QuoteMode mode, List<InstrumentKey> instruments) {
        if (mode == null) {
            throw new MarketDataValidationException("quote mode must not be null");
        }
        if (instruments == null || instruments.isEmpty()) {
            throw new MarketDataValidationException("at least one instrument is required");
        }

        Set<InstrumentKey> unique = new LinkedHashSet<>();
        for (InstrumentKey instrument : instruments) {
            if (instrument == null) {
                throw new MarketDataValidationException("instrument keys must not contain null");
            }
            unique.add(instrument);
        }
        if (unique.size() > MAX_QUOTE_INSTRUMENTS) {
            throw new MarketDataValidationException(
                    "quote request exceeds the limit of " + MAX_QUOTE_INSTRUMENTS + " instruments");
        }

        Map<Exchange, List<String>> grouped = new LinkedHashMap<>();
        for (InstrumentKey instrument : unique) {
            grouped.computeIfAbsent(exchange(instrument.segment()), ignored -> new ArrayList<>())
                    .add(instrument.symbolToken());
        }

        Instant receivedAt = marketStatusResolver.now();
        MarketStatus status = marketStatusResolver.resolve(receivedAt);
        try {
            return broker.quotes(providerMode(mode), grouped).stream()
                    .map(quote -> quoteMapper.toDomain(quote, receivedAt, status))
                    .toList();
        } catch (com.tradingassistant.broker.exception.BrokerException exception) {
            throw MarketDataFailureMapper.translate(exception);
        }
    }

    private static com.tradingassistant.broker.model.QuoteMode providerMode(QuoteMode mode) {
        return switch (mode) {
            case LTP -> com.tradingassistant.broker.model.QuoteMode.LTP;
            case OHLC -> com.tradingassistant.broker.model.QuoteMode.OHLC;
            case FULL -> com.tradingassistant.broker.model.QuoteMode.FULL;
        };
    }

    static Exchange exchange(Segment segment) {
        return switch (segment) {
            case NSE_CASH -> Exchange.NSE;
            case NSE_FO -> Exchange.NFO;
            case BSE_CASH -> Exchange.BSE;
            case BSE_FO -> Exchange.BFO;
            case MCX_FO -> Exchange.MCX;
            case NSE_COM -> throw new MarketDataValidationException(
                    "The configured provider does not support quote or feed routing for segment NCO.");
            case CDS -> Exchange.CDS;
            case NCDEX -> Exchange.NCDEX;
        };
    }
}

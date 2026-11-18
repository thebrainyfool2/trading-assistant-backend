package com.tradingassistant.market.service;

import com.tradingassistant.broker.api.BrokerProvider;
import com.tradingassistant.broker.exception.BrokerException;
import com.tradingassistant.broker.model.CandleInterval;
import com.tradingassistant.broker.model.CandleRequest;
import com.tradingassistant.market.domain.Candle;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.market.exception.MarketDataValidationException;
import com.tradingassistant.market.mapping.CandleMapper;
import com.tradingassistant.market.time.MarketStatusResolver;

import java.time.Instant;
import java.util.List;

/**
 * Broker-independent historical data access. Domain instants are converted to
 * Indian market time only at the provider request boundary.
 */
public final class HistoricalDataService {

    private final BrokerProvider broker;
    private final CandleMapper candleMapper;
    private final MarketStatusResolver marketTime;

    public HistoricalDataService(BrokerProvider broker,
                                 CandleMapper candleMapper,
                                 MarketStatusResolver marketTime) {
        this.broker = broker;
        this.candleMapper = candleMapper;
        this.marketTime = marketTime;
    }

    public List<Candle> candles(InstrumentKey instrument, Interval interval, Instant from, Instant to) {
        if (instrument == null) {
            throw new MarketDataValidationException("instrument must not be null");
        }
        if (interval == null) {
            throw new MarketDataValidationException("interval must not be null");
        }
        if (from == null || to == null) {
            throw new MarketDataValidationException("from and to must not be null");
        }
        if (from.isAfter(to)) {
            throw new MarketDataValidationException("from must not be after to");
        }

        CandleRequest request;
        try {
            request = new CandleRequest(
                    QuoteService.exchange(instrument.segment()),
                    instrument.symbolToken(),
                    providerInterval(interval),
                    marketTime.toMarketTime(from),
                    marketTime.toMarketTime(to));
        } catch (IllegalArgumentException exception) {
            throw new MarketDataValidationException("The candle request is invalid.", exception);
        }

        try {
            return broker.candles(request).stream()
                    .map(candle -> candleMapper.toDomain(candle, instrument, interval))
                    .toList();
        } catch (BrokerException exception) {
            throw MarketDataFailureMapper.translate(exception);
        }
    }

    private static CandleInterval providerInterval(Interval interval) {
        return switch (interval) {
            case ONE_MINUTE -> CandleInterval.ONE_MINUTE;
            case THREE_MINUTE -> CandleInterval.THREE_MINUTE;
            case FIVE_MINUTE -> CandleInterval.FIVE_MINUTE;
            case TEN_MINUTE -> CandleInterval.TEN_MINUTE;
            case FIFTEEN_MINUTE -> CandleInterval.FIFTEEN_MINUTE;
            case THIRTY_MINUTE -> CandleInterval.THIRTY_MINUTE;
            case ONE_HOUR -> CandleInterval.ONE_HOUR;
            case ONE_DAY -> CandleInterval.ONE_DAY;
        };
    }
}

package com.tradingassistant.market.mapping;

import com.tradingassistant.market.domain.Candle;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.market.exception.MarketDataMappingException;

/**
 * Converts provider candles to UTC-instant domain candles while retaining the
 * requested instrument and interval.
 */
public final class CandleMapper {

    public Candle toDomain(com.tradingassistant.broker.model.Candle source,
                           InstrumentKey instrument,
                           Interval interval) {
        if (source == null) {
            throw new MarketDataMappingException("A candle row was null.");
        }
        try {
            return new Candle(
                    instrument,
                    interval,
                    source.timestamp().toInstant(),
                    source.open(),
                    source.high(),
                    source.low(),
                    source.close(),
                    source.volume());
        } catch (RuntimeException exception) {
            throw new MarketDataMappingException(
                    "A candle could not be mapped to the domain: " + exception.getMessage(), exception);
        }
    }
}

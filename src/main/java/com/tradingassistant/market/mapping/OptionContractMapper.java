package com.tradingassistant.market.mapping;

import com.tradingassistant.market.domain.Instrument;
import com.tradingassistant.market.domain.InstrumentType;
import com.tradingassistant.market.domain.OptionContract;
import com.tradingassistant.market.exception.MarketDataMappingException;

import java.util.Optional;

/**
 * Builds an option contract only when the source instrument carries the required
 * option fields. Non-option instruments return empty; an incomplete option row
 * is a mapping failure, not a silently ignored contract.
 */
public final class OptionContractMapper {

    public Optional<OptionContract> fromInstrument(Instrument instrument) {
        if (instrument == null) {
            throw new MarketDataMappingException("An option source instrument was null.");
        }
        if (instrument.type() != InstrumentType.OPTION) {
            return Optional.empty();
        }
        if (instrument.expiry() == null) {
            throw new MarketDataMappingException("An option instrument was missing its expiry.");
        }
        if (instrument.strike() == null) {
            throw new MarketDataMappingException("An option instrument was missing its strike.");
        }
        if (instrument.optionType() == null) {
            throw new MarketDataMappingException("An option instrument was missing its option type.");
        }
        if (instrument.underlying() == null || instrument.underlying().isBlank()) {
            throw new MarketDataMappingException("An option instrument was missing its underlying.");
        }
        try {
            return Optional.of(new OptionContract(
                    instrument,
                    instrument.underlying(),
                    instrument.expiry(),
                    instrument.strike(),
                    instrument.optionType()));
        } catch (RuntimeException exception) {
            throw new MarketDataMappingException(
                    "Option instrument " + instrument.symbolToken() + " could not be mapped.", exception);
        }
    }
}

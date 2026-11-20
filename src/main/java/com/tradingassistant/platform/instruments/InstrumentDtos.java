package com.tradingassistant.platform.instruments;

import com.tradingassistant.market.domain.Instrument;
import com.tradingassistant.market.domain.InstrumentType;
import com.tradingassistant.market.domain.OptionType;
import com.tradingassistant.market.domain.Segment;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Stable read-only projections for instrument search and selection. */
public final class InstrumentDtos {

    private InstrumentDtos() {
    }

    public record InstrumentView(
            Segment segment,
            String symbolToken,
            String symbol,
            String tradingSymbol,
            String displayName,
            InstrumentType type,
            String underlying,
            LocalDate expiry,
            BigDecimal strike,
            OptionType optionType,
            long lotSize,
            BigDecimal tickSize) {

        public static InstrumentView from(Instrument instrument) {
            return new InstrumentView(instrument.segment(),
                    instrument.symbolToken(), instrument.symbol(),
                    instrument.tradingSymbol(), instrument.displayName(),
                    instrument.type(), instrument.underlying(), instrument.expiry(),
                    instrument.strike(), instrument.optionType(),
                    instrument.lotSize(), instrument.tickSize());
        }
    }
}

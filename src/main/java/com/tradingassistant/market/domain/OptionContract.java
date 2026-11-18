package com.tradingassistant.market.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A validated option contract. Lot size and tick size are exposed from the
 * underlying {@link Instrument} rather than duplicated.
 */
public record OptionContract(
        Instrument instrument,
        String underlying,
        LocalDate expiry,
        BigDecimal strike,
        OptionType optionType
) {

    public OptionContract {
        instrument = DomainValidation.required(instrument, "instrument");
        if (instrument.type() != InstrumentType.OPTION) {
            throw new IllegalArgumentException("instrument must be an OPTION");
        }
        underlying = DomainValidation.text(underlying, "underlying");
        expiry = DomainValidation.required(expiry, "expiry");
        strike = DomainValidation.positive(strike, "strike");
        optionType = DomainValidation.required(optionType, "optionType");
        if (instrument.tickSize().signum() <= 0) {
            throw new IllegalArgumentException("option instrument tickSize must be positive");
        }
        if (instrument.lotSize() <= 0) {
            throw new IllegalArgumentException("option instrument lotSize must be positive");
        }
        if (!instrument.segment().isDerivative()) {
            throw new IllegalArgumentException("option contract must belong to a derivative segment");
        }
        if (instrument.expiry() != null && !instrument.expiry().equals(expiry)) {
            throw new IllegalArgumentException("contract expiry must match the instrument expiry");
        }
        if (instrument.strike() != null && instrument.strike().compareTo(strike) != 0) {
            throw new IllegalArgumentException("contract strike must match the instrument strike");
        }
        if (instrument.optionType() != null && instrument.optionType() != optionType) {
            throw new IllegalArgumentException("contract option type must match the instrument option type");
        }
        if (instrument.underlying() != null && !instrument.underlying().equalsIgnoreCase(underlying)) {
            throw new IllegalArgumentException("contract underlying must match the instrument underlying");
        }
    }

    public long lotSize() {
        return instrument.lotSize();
    }

    public BigDecimal tickSize() {
        return instrument.tickSize();
    }

    public Exchange exchange() {
        return instrument.exchange();
    }

    public Segment segment() {
        return instrument.segment();
    }
}

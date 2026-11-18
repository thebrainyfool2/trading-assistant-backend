package com.tradingassistant.market.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A listed instrument in the application's language.
 *
 * <p>The optional derivative fields are populated when the provider supplies
 * them. An instrument may be partial for non-option rows; {@link OptionContract}
 * enforces the stronger contract required for options.</p>
 */
public record Instrument(
        InstrumentKey key,
        String symbol,
        String tradingSymbol,
        String displayName,
        InstrumentType type,
        BigDecimal tickSize,
        long lotSize,
        BigDecimal freezeQuantity,
        String isin,
        Currency currency,
        LocalDate expiry,
        BigDecimal strike,
        OptionType optionType,
        String underlying
) {

    public Instrument {
        key = DomainValidation.required(key, "key");
        symbol = DomainValidation.text(symbol, "symbol");
        tradingSymbol = DomainValidation.text(tradingSymbol, "tradingSymbol");
        displayName = DomainValidation.text(
                displayName == null || displayName.isBlank() ? symbol : displayName,
                "displayName");
        type = DomainValidation.required(type, "type");
        tickSize = DomainValidation.nonNegative(
                DomainValidation.required(tickSize, "tickSize"), "tickSize");
        DomainValidation.nonNegative(lotSize, "lotSize");
        freezeQuantity = DomainValidation.nonNegative(freezeQuantity, "freezeQuantity");
        isin = DomainValidation.optionalText(isin);
        currency = currency == null ? Currency.INR : currency;
        strike = DomainValidation.nonNegative(strike, "strike");
        underlying = DomainValidation.optionalText(underlying);
        if (optionType != null && type != InstrumentType.OPTION) {
            throw new IllegalArgumentException("optionType is only valid for OPTION instruments");
        }
    }

    public Exchange exchange() {
        return key.exchange();
    }

    public Segment segment() {
        return key.segment();
    }

    public String symbolToken() {
        return key.symbolToken();
    }

    public boolean isOption() {
        return type == InstrumentType.OPTION;
    }
}

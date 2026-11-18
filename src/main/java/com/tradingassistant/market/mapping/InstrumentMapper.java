package com.tradingassistant.market.mapping;

import com.tradingassistant.market.domain.Currency;
import com.tradingassistant.market.domain.Instrument;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.InstrumentType;
import com.tradingassistant.market.domain.OptionType;
import com.tradingassistant.market.domain.Segment;
import com.tradingassistant.market.exception.MarketDataMappingException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts a token-free provider instrument row into the application domain.
 * Provider-specific expiry and instrument-type spellings remain in this
 * boundary mapper.
 */
public final class InstrumentMapper {

    private static final Pattern OPTION_SUFFIX =
            Pattern.compile("(CE|PE)(\\d+)?(?:F?[A-Z]{3}\\d{2,4})?$");

    private static final List<DateTimeFormatter> EXPIRY_FORMATS = List.of(
            formatter("ddMMMyyyy"),
            formatter("dd-MMM-yyyy"),
            formatter("yyyy-MM-dd"),
            formatter("ddMMMyy"));

    public Instrument toDomain(com.tradingassistant.broker.model.Instrument source) {
        if (source == null) {
            throw new MarketDataMappingException("An instrument row was null.");
        }
        try {
            Segment segment = segment(source.exchangeSegment());
            InstrumentType type = instrumentType(source.instrumentType());
            String tradingSymbol = firstNonBlank(source.tradingSymbol(), source.symbolToken());
            String displayName = firstNonBlank(source.name(), tradingSymbol);
            OptionType optionType = optionType(source.tradingSymbol(), type);
            String underlying = underlying(source.name(), source.tradingSymbol(), type);
            BigDecimal strike = source.strike();
            if (type != InstrumentType.OPTION && strike != null && strike.signum() < 0) {
                // Some cash instruments use a negative strike as a sentinel.
                strike = null;
            }
            if (source.lotSize() == null) {
                throw new MarketDataMappingException("The instrument lot size was missing.");
            }
            long lotSize = source.lotSize();
            if (type != InstrumentType.OPTION && lotSize < 0) {
                // Cash/index rows use a negative lot size as an unknown sentinel.
                lotSize = 0;
            }
            BigDecimal freezeQuantity = source.freezeQuantity();
            if (freezeQuantity != null && freezeQuantity.signum() < 0) {
                freezeQuantity = null;
            }

            return new Instrument(
                    new InstrumentKey(segment, source.symbolToken()),
                    tradingSymbol,
                    tradingSymbol,
                    displayName,
                    type,
                    source.tickSize(),
                    lotSize,
                    freezeQuantity,
                    null,
                    Currency.INR,
                    parseExpiry(source.expiry()),
                    strike,
                    optionType,
                    underlying);
        } catch (MarketDataMappingException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new MarketDataMappingException(
                    "Instrument " + source.symbolToken() + " could not be mapped to the domain.", exception);
        }
    }

    private static DateTimeFormatter formatter(String pattern) {
        return new DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .appendPattern(pattern)
                .toFormatter(Locale.ENGLISH);
    }

    static Segment segment(String exchangeSegment) {
        try {
            return Segment.fromCode(exchangeSegment);
        } catch (IllegalArgumentException exception) {
            throw new MarketDataMappingException("Unknown instrument segment: " + exchangeSegment, exception);
        }
    }

    static InstrumentType instrumentType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return InstrumentType.OTHER;
        }
        String type = rawType.trim().toUpperCase(Locale.ROOT);
        if (type.startsWith("OPT")) {
            return InstrumentType.OPTION;
        }
        if (type.startsWith("FUT")) {
            return InstrumentType.FUTURE;
        }
        if (type.equals("EQ") || type.equals("BE") || type.equals("SM")
                || type.equals("ETF") || type.startsWith("EQ")) {
            return InstrumentType.EQUITY;
        }
        if (type.contains("INDEX") || type.equals("IDX") || type.equals("AMXIDX")) {
            return InstrumentType.INDEX;
        }
        if (type.contains("CUR")) {
            return InstrumentType.CURRENCY;
        }
        if (type.contains("COM")) {
            return InstrumentType.COMMODITY;
        }
        return InstrumentType.OTHER;
    }

    static OptionType optionType(String tradingSymbol, InstrumentType type) {
        if (type != InstrumentType.OPTION) {
            return null;
        }
        String symbol = tradingSymbol == null ? "" : tradingSymbol.trim().toUpperCase(Locale.ROOT);
        Matcher matcher = OPTION_SUFFIX.matcher(symbol);
        if (matcher.find()) {
            return "CE".equals(matcher.group(1)) ? OptionType.CALL : OptionType.PUT;
        }
        throw new MarketDataMappingException("An option instrument did not identify CALL or PUT in its symbol.");
    }

    static LocalDate parseExpiry(String rawExpiry) {
        if (rawExpiry == null || rawExpiry.isBlank()) {
            return null;
        }
        String expiry = rawExpiry.trim().toUpperCase(Locale.ROOT);
        for (DateTimeFormatter formatter : EXPIRY_FORMATS) {
            try {
                return LocalDate.parse(expiry, formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next documented/provider format.
            }
        }
        throw new MarketDataMappingException("Unsupported instrument expiry format: " + rawExpiry);
    }

    static String underlying(String name, String tradingSymbol, InstrumentType type) {
        if (type != InstrumentType.OPTION) {
            return null;
        }
        String underlying = name == null ? "" : name.trim();
        if (!underlying.isBlank()) {
            return underlying;
        }
        String symbol = tradingSymbol == null ? "" : tradingSymbol.trim().toUpperCase(Locale.ROOT);
        underlying = symbol.replaceFirst("\\d{2}[A-Z]{3}\\d{2,4}.*(CE|PE)$", "").trim();
        if (underlying.isBlank()) {
            throw new MarketDataMappingException("An option instrument did not identify its underlying.");
        }
        return underlying;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        return second == null ? "" : second.trim();
    }
}

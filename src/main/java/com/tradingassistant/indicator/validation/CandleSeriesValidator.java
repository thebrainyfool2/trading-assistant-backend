package com.tradingassistant.indicator.validation;

import com.tradingassistant.indicator.exception.IndicatorValidationException;
import com.tradingassistant.market.domain.Candle;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared validation for historical and incremental input. Timestamps must be
 * strictly increasing so duplicate and out-of-order candles cannot silently
 * corrupt recursive indicator state.
 */
public final class CandleSeriesValidator {

    private CandleSeriesValidator() {
    }

    public static void validate(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) {
            throw new IndicatorValidationException("At least one candle is required.");
        }
        Instant previous = null;
        for (Candle candle : candles) {
            validate(candle);
            if (previous != null && !candle.timestamp().isAfter(previous)) {
                if (candle.timestamp().equals(previous)) {
                    throw new IndicatorValidationException(
                            "Duplicate candle timestamp: " + candle.timestamp());
                }
                throw new IndicatorValidationException(
                        "Candles must be in strictly ascending timestamp order: " + candle.timestamp());
            }
            previous = candle.timestamp();
        }
    }

    public static void validateNext(Candle candle, Instant previousTimestamp) {
        validate(candle);
        if (previousTimestamp != null && !candle.timestamp().isAfter(previousTimestamp)) {
            if (candle.timestamp().equals(previousTimestamp)) {
                throw new IndicatorValidationException(
                        "Duplicate candle timestamp: " + candle.timestamp());
            }
            throw new IndicatorValidationException(
                    "Candles must be in strictly ascending timestamp order: " + candle.timestamp());
        }
    }

    public static void validate(Candle candle) {
        if (candle == null) {
            throw new IndicatorValidationException("Candle must not be null.");
        }
        if (candle.timestamp() == null) {
            throw new IndicatorValidationException("Candle timestamp must not be null.");
        }
        if (candle.open() == null || candle.high() == null
                || candle.low() == null || candle.close() == null) {
            throw new IndicatorValidationException(
                    "Candle OHLC values must not be null at " + candle.timestamp());
        }
        if (candle.open().signum() < 0 || candle.high().signum() < 0
                || candle.low().signum() < 0 || candle.close().signum() < 0) {
            throw new IndicatorValidationException(
                    "Candle prices must not be negative at " + candle.timestamp());
        }
        if (candle.high().compareTo(candle.low()) < 0) {
            throw new IndicatorValidationException(
                    "Candle high must be greater than or equal to low at " + candle.timestamp());
        }
        if (candle.open().compareTo(candle.high()) > 0
                || candle.open().compareTo(candle.low()) < 0
                || candle.close().compareTo(candle.high()) > 0
                || candle.close().compareTo(candle.low()) < 0) {
            throw new IndicatorValidationException(
                    "Candle OHLC values are inconsistent at " + candle.timestamp());
        }
        if (candle.volume() < 0) {
            throw new IndicatorValidationException(
                    "Candle volume must not be negative at " + candle.timestamp());
        }
    }

    /**
     * Finds interval-sized gaps without rejecting them: weekends, holidays and
     * exchange closures are legitimate in daily data. Indicators never fill
     * or synthesize candles.
     */
    public static List<CandleGap> findGaps(List<Candle> candles) {
        validate(candles);
        List<CandleGap> gaps = new ArrayList<>();
        for (int i = 1; i < candles.size(); i++) {
            Candle previous = candles.get(i - 1);
            Candle current = candles.get(i);
            Duration expected = previous.interval().duration();
            Duration actual = Duration.between(previous.timestamp(), current.timestamp());
            if (actual.compareTo(expected) > 0) {
                long missing = actual.dividedBy(expected) - 1;
                gaps.add(new CandleGap(previous.timestamp(), current.timestamp(), expected, missing));
            }
        }
        return List.copyOf(gaps);
    }
}

package com.tradingassistant.broker.model;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Historical candle request. Times are interpreted in the broker's Indian market
 * timezone because SmartAPI accepts an unzoned {@code yyyy-MM-dd HH:mm} value.
 */
public record CandleRequest(
        Exchange exchange,
        String symbolToken,
        CandleInterval interval,
        LocalDateTime from,
        LocalDateTime to
) {

    public CandleRequest {
        Objects.requireNonNull(exchange, "exchange must not be null");
        Objects.requireNonNull(interval, "interval must not be null");
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        if (symbolToken == null || symbolToken.isBlank()) {
            throw new IllegalArgumentException("symbolToken must not be blank");
        }
        symbolToken = symbolToken.trim();
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from must not be after to");
        }
        long spanDays = ChronoUnit.DAYS.between(from.toLocalDate(), to.toLocalDate());
        if (spanDays > interval.maxDays()) {
            throw new IllegalArgumentException(
                    "Requested span exceeds the documented maximum of " + interval.maxDays()
                            + " days for " + interval.code());
        }
    }
}

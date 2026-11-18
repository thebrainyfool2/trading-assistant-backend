package com.tradingassistant.options.math;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Converts an expiry date and a capture instant into a year fraction.
 *
 * <p>The horizon is counted in whole exchange calendar days and divided by the
 * configured day count, which keeps the value deterministic and independent of
 * intraday clocks. An expiry on the capture date yields zero.</p>
 */
public final class ExpiryTime {

    private ExpiryTime() {
    }

    public static long daysToExpiry(LocalDate expiry, Instant at, ZoneId exchangeZone) {
        Objects.requireNonNull(expiry, "expiry must not be null");
        Objects.requireNonNull(at, "at must not be null");
        Objects.requireNonNull(exchangeZone, "exchangeZone must not be null");
        LocalDate marketDate = at.atZone(exchangeZone).toLocalDate();
        long days = ChronoUnit.DAYS.between(marketDate, expiry);
        return Math.max(0L, days);
    }

    public static BigDecimal yearsToExpiry(LocalDate expiry, Instant at, ZoneId exchangeZone,
                                           int dayCount) {
        if (dayCount < 1) {
            throw new IllegalArgumentException("dayCount must be positive");
        }
        long days = daysToExpiry(expiry, at, exchangeZone);
        return BigDecimal.valueOf(days)
                .divide(BigDecimal.valueOf(dayCount), DecimalMath.CONTEXT);
    }
}

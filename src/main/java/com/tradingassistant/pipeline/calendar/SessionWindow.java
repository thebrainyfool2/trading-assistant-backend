package com.tradingassistant.pipeline.calendar;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * A recurring local-time trading window for one exchange calendar. The window is
 * expressed in local market time so it stays stable across the year; conversion
 * to UTC happens per trading date.
 */
public record SessionWindow(LocalTime open, LocalTime close) {

    public SessionWindow {
        if (open == null || close == null) {
            throw new IllegalArgumentException("Window open and close must not be null");
        }
        if (!close.isAfter(open)) {
            throw new IllegalArgumentException("Window close must be after open");
        }
        if (Duration.between(open, close).toMinutes() < 1) {
            throw new IllegalArgumentException("Window must be at least one minute long");
        }
    }

    public Instant start(LocalDate date, ZoneId zone) {
        return ZonedDateTime.of(date, open, zone).toInstant();
    }

    public Instant end(LocalDate date, ZoneId zone) {
        return ZonedDateTime.of(date, close, zone).toInstant();
    }

    public TradingSession session(String calendar, LocalDate date, ZoneId zone) {
        return new TradingSession(calendar, date, start(date, zone), end(date, zone));
    }

    public boolean contains(LocalTime time) {
        return !time.isBefore(open) && time.isBefore(close);
    }
}

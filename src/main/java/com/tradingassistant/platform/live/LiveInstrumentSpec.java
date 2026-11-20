package com.tradingassistant.platform.live;

import com.tradingassistant.market.domain.Segment;

import java.util.Locale;

/**
 * One configured live-market reference.
 *
 * <p>A reference is either a numeric broker token or a symbol alias resolved
 * against the instrument master. Aliases make configuration readable without
 * hard-coding provider tokens that change between contracts.</p>
 */
record LiveInstrumentSpec(Segment segment, String reference, boolean numericToken) {

    static LiveInstrumentSpec parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Instrument must be SEGMENT:TOKEN_OR_SYMBOL");
        }
        String value = raw.trim();
        int separator = value.indexOf(':');
        if (separator <= 0 || separator == value.length() - 1) {
            throw new IllegalArgumentException("Instrument must be SEGMENT:TOKEN_OR_SYMBOL: " + value);
        }
        Segment segment = Segment.fromCode(value.substring(0, separator));
        String reference = value.substring(separator + 1).trim().toUpperCase(Locale.ROOT);
        if (reference.isEmpty()) {
            throw new IllegalArgumentException("Instrument reference must not be blank: " + value);
        }
        return new LiveInstrumentSpec(segment, reference, reference.matches("\\d+"));
    }
}

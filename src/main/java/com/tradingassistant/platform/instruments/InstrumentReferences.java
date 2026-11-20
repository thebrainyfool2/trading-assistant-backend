package com.tradingassistant.platform.instruments;

import com.tradingassistant.common.api.ErrorCode;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.Segment;
import com.tradingassistant.platform.error.PlatformException;

import java.util.Locale;

/** Parses and formats the stable {@code SEGMENT:TOKEN} API reference. */
public final class InstrumentReferences {

    private InstrumentReferences() {
    }

    public static InstrumentKey parse(String value) {
        if (value == null || value.isBlank()) {
            throw new PlatformException(ErrorCode.VALIDATION_FAILED,
                    "Instrument must be SEGMENT:TOKEN.");
        }
        String text = value.trim();
        int separator = text.indexOf(':');
        if (separator <= 0 || separator == text.length() - 1) {
            throw new PlatformException(ErrorCode.VALIDATION_FAILED,
                    "Instrument must be SEGMENT:TOKEN: " + value);
        }
        try {
            Segment segment = Segment.fromCode(text.substring(0, separator));
            String token = text.substring(separator + 1).trim()
                    .toUpperCase(Locale.ROOT);
            if (token.isEmpty()) {
                throw new IllegalArgumentException("Instrument token must not be blank.");
            }
            return new InstrumentKey(segment, token);
        } catch (IllegalArgumentException failure) {
            throw new PlatformException(ErrorCode.VALIDATION_FAILED,
                    failure.getMessage());
        }
    }

    public static String format(InstrumentKey key) {
        return key.segment().name() + ':' + key.symbolToken();
    }
}

package com.tradingassistant.qualification.rule;

import com.tradingassistant.intelligence.domain.ConfidenceLevel;
import com.tradingassistant.intelligence.domain.ParticipationState;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Read-only helpers shared by the built-in qualification rules.
 *
 * <p>Nothing here decides a condition: the rules do. The helpers only order the
 * descriptive enums and format metadata deterministically.</p>
 */
final class QualificationSupport {

    private QualificationSupport() {
    }

    /**
     * Ranks descriptive confidence levels so a minimum can be compared. Unknown is
     * deliberately unranked: it means the level could not be established.
     */
    static int confidenceRank(ConfidenceLevel level) {
        return switch (level) {
            case HIGH -> 3;
            case MEDIUM -> 2;
            case LOW -> 1;
            case UNKNOWN -> 0;
        };
    }

    /**
     * Ranks participation states so a minimum can be compared.
     */
    static int participationRank(ParticipationState state) {
        return switch (state) {
            case STRONG -> 3;
            case AVERAGE -> 2;
            case WEAK -> 1;
            case UNKNOWN -> 0;
        };
    }

    static Map<String, String> metadata(String... keysAndValues) {
        if (keysAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("metadata requires key/value pairs");
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (int index = 0; index < keysAndValues.length; index += 2) {
            values.put(keysAndValues[index], keysAndValues[index + 1]);
        }
        return Map.copyOf(values);
    }

    static String instant(Instant value) {
        return value == null ? "unavailable" : value.toString();
    }

    static String name(Enum<?> value) {
        return value == null ? "UNKNOWN" : value.name().toUpperCase(Locale.ROOT);
    }
}

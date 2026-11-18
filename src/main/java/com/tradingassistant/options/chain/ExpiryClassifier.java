package com.tradingassistant.options.chain;

import com.tradingassistant.options.domain.ExpiryKind;
import com.tradingassistant.options.domain.ExpiryProximity;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Classifies expiries by cadence and by their position in the chain.
 *
 * <p>The monthly expiry is the last listed expiry of its calendar month; every
 * other expiry of that month is weekly. Proximity is purely ordinal: the first
 * expiry is nearest, the second next, and everything later is far.</p>
 */
public final class ExpiryClassifier {

    private ExpiryClassifier() {
    }

    public static Set<LocalDate> monthlyExpiries(List<LocalDate> expiries) {
        Objects.requireNonNull(expiries, "expiries must not be null");
        return expiries.stream()
                .collect(Collectors.groupingBy(YearMonth::from,
                        Collectors.maxBy(LocalDate::compareTo)))
                .values()
                .stream()
                .flatMap(java.util.Optional::stream)
                .collect(Collectors.toUnmodifiableSet());
    }

    public static ExpiryKind kind(LocalDate expiry, Set<LocalDate> monthlyExpiries) {
        Objects.requireNonNull(expiry, "expiry must not be null");
        Objects.requireNonNull(monthlyExpiries, "monthlyExpiries must not be null");
        return monthlyExpiries.contains(expiry) ? ExpiryKind.MONTHLY : ExpiryKind.WEEKLY;
    }

    public static ExpiryProximity proximity(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("index must not be negative");
        }
        if (index == 0) {
            return ExpiryProximity.NEAREST;
        }
        if (index == 1) {
            return ExpiryProximity.NEXT;
        }
        return ExpiryProximity.FAR;
    }
}

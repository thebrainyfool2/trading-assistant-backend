package com.tradingassistant.options.domain;

import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.Segment;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable structure of one underlying's option chain: every expiry in
 * ascending date order, each holding strike groups in ascending strike order.
 *
 * <p>Validation happens at construction, so an instance always represents an
 * ordered, duplicate-free chain whose legs share one underlying and segment.</p>
 */
public record OptionChain(
        InstrumentKey spotInstrument,
        String underlying,
        Segment segment,
        List<ExpiryChain> expiries) {

    public OptionChain {
        spotInstrument = Objects.requireNonNull(spotInstrument, "spotInstrument must not be null");
        if (underlying == null || underlying.isBlank()) {
            throw new IllegalArgumentException("underlying must not be blank");
        }
        underlying = underlying.trim().toUpperCase(java.util.Locale.ROOT);
        segment = Objects.requireNonNull(segment, "segment must not be null");
        if (!segment.isDerivative()) {
            throw new IllegalArgumentException("option chain segment must be a derivative segment");
        }
        if (expiries == null || expiries.isEmpty()) {
            throw new IllegalArgumentException("option chain must hold at least one expiry");
        }
        if (expiries.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("expiries must not contain null entries");
        }
        List<ExpiryChain> ordered = List.copyOf(expiries);
        for (int index = 1; index < ordered.size(); index++) {
            if (!ordered.get(index - 1).expiry().isBefore(ordered.get(index).expiry())) {
                throw new IllegalArgumentException("expiries must be strictly ascending");
            }
        }
        for (ExpiryChain expiry : ordered) {
            for (Strike leg : expiry.legs()) {
                if (!leg.contract().underlying().equalsIgnoreCase(underlying)) {
                    throw new IllegalArgumentException("every leg must share the chain underlying");
                }
                if (leg.contract().segment() != segment) {
                    throw new IllegalArgumentException("every leg must share the chain segment");
                }
                if (!leg.contract().exchange().equals(spotInstrument.exchange())) {
                    throw new IllegalArgumentException(
                            "every leg must trade on the spot instrument's exchange");
                }
            }
        }
        expiries = ordered;
    }

    public static OptionChain of(InstrumentKey spotInstrument, String underlying, Segment segment,
                                 List<ExpiryChain> expiries) {
        return new OptionChain(spotInstrument, underlying, segment, expiries);
    }

    public Optional<ExpiryChain> nearestExpiry() {
        return expiries.stream().findFirst();
    }

    public Optional<LocalDate> nearestExpiryDate() {
        return nearestExpiry().map(ExpiryChain::expiry);
    }

    public Optional<ExpiryChain> expiry(LocalDate date) {
        return expiries.stream().filter(entry -> entry.expiry().equals(date)).findFirst();
    }

    public List<Strike> legs() {
        return expiries.stream().flatMap(expiry -> expiry.legs().stream()).toList();
    }

    public int strikeCount() {
        return (int) expiries.stream()
                .flatMap(expiry -> expiry.strikes().stream())
                .map(StrikeGroup::strike)
                .distinct()
                .count();
    }

    public int legCount() {
        return legs().size();
    }
}

package com.tradingassistant.options.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable chain of one expiry: strike groups in ascending strike order plus
 * the expiry classification.
 *
 * @param expiry       expiry date
 * @param kind         weekly or monthly classification
 * @param proximity    position of the expiry inside its parent chain
 * @param daysToExpiry calendar days between capture and expiry
 * @param strikes      strike groups ordered by ascending strike
 */
public record ExpiryChain(
        LocalDate expiry,
        ExpiryKind kind,
        ExpiryProximity proximity,
        long daysToExpiry,
        List<StrikeGroup> strikes) {

    public ExpiryChain {
        expiry = Objects.requireNonNull(expiry, "expiry must not be null");
        kind = Objects.requireNonNull(kind, "kind must not be null");
        proximity = Objects.requireNonNull(proximity, "proximity must not be null");
        if (daysToExpiry < 0) {
            throw new IllegalArgumentException("daysToExpiry must not be negative");
        }
        if (strikes == null || strikes.isEmpty()) {
            throw new IllegalArgumentException("expiry chain must hold at least one strike");
        }
        if (strikes.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("strikes must not contain null groups");
        }
        List<StrikeGroup> ordered = List.copyOf(strikes);
        for (int index = 1; index < ordered.size(); index++) {
            if (ordered.get(index - 1).strike().compareTo(ordered.get(index).strike()) >= 0) {
                throw new IllegalArgumentException("strikes must be strictly ascending");
            }
        }
        for (StrikeGroup group : ordered) {
            for (Strike leg : group.legs()) {
                if (!leg.expiry().equals(expiry)) {
                    throw new IllegalArgumentException("leg expiry must match the expiry chain");
                }
            }
        }
        strikes = ordered;
    }

    public List<Strike> legs() {
        return strikes.stream().flatMap(group -> group.legs().stream()).toList();
    }

    public int legCount() {
        return legs().size();
    }

    public List<Strike> calls() {
        return legs().stream().filter(Strike::isCall).toList();
    }

    public List<Strike> puts() {
        return legs().stream().filter(leg -> !leg.isCall()).toList();
    }

    /** Strike groups marked at the money, empty when the chain has no ATM strike. */
    public Optional<StrikeGroup> atTheMoney() {
        return strikes.stream().filter(StrikeGroup::atTheMoney).findFirst();
    }

    /**
     * Strike nearest to the given price, used when no strike falls inside the
     * configured ATM band.
     */
    public Optional<StrikeGroup> nearestTo(BigDecimal price) {
        return strikes.stream().min((left, right) -> left.strike().subtract(price).abs()
                .compareTo(right.strike().subtract(price).abs()));
    }

    public Optional<Strike> leg(BigDecimal strike, com.tradingassistant.market.domain.OptionType type) {
        return strikes.stream()
                .filter(group -> group.strike().compareTo(strike) == 0)
                .findFirst()
                .flatMap(group -> group.leg(type));
    }
}

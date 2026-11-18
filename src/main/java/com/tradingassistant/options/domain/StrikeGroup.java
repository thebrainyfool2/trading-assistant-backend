package com.tradingassistant.options.domain;

import com.tradingassistant.market.domain.OptionType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Both option types listed at one strike of one expiry. Either leg may be
 * absent, and a group never holds two legs of the same type.
 */
public record StrikeGroup(
        BigDecimal strike,
        Optional<Strike> call,
        Optional<Strike> put) {

    public StrikeGroup {
        strike = Objects.requireNonNull(strike, "strike must not be null");
        if (strike.signum() <= 0) {
            throw new IllegalArgumentException("strike must be positive");
        }
        call = call == null ? Optional.empty() : call;
        put = put == null ? Optional.empty() : put;
        BigDecimal groupStrike = strike;
        call.ifPresent(leg -> requireType(leg, OptionType.CALL, groupStrike));
        put.ifPresent(leg -> requireType(leg, OptionType.PUT, groupStrike));
        if (call.isEmpty() && put.isEmpty()) {
            throw new IllegalArgumentException("strike group must hold at least one leg");
        }
        if (!call.isEmpty() && !put.isEmpty()
                && !call.get().expiry().equals(put.get().expiry())) {
            throw new IllegalArgumentException("call and put legs must share the expiry");
        }
    }

    public static StrikeGroup of(BigDecimal strike, Strike call, Strike put) {
        return new StrikeGroup(strike,
                call == null ? Optional.empty() : Optional.of(call),
                put == null ? Optional.empty() : Optional.of(put));
    }

    public Optional<Strike> leg(OptionType optionType) {
        return optionType.isCall() ? call : put;
    }

    public List<Strike> legs() {
        return call.map(c -> put.map(p -> List.of(c, p)).orElseGet(() -> List.of(c)))
                .orElseGet(() -> put.map(List::of).orElseGet(List::of));
    }

    public boolean atTheMoney() {
        return legs().stream().anyMatch(Strike::isAtTheMoney);
    }

    private static void requireType(Strike leg, OptionType expected, BigDecimal strike) {
        Objects.requireNonNull(leg, "leg must not be null");
        if (leg.optionType() != expected) {
            throw new IllegalArgumentException("strike group leg type must be " + expected);
        }
        if (leg.strike().compareTo(strike) != 0) {
            throw new IllegalArgumentException("strike group leg strike must match the group strike");
        }
    }
}

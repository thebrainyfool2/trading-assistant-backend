package com.tradingassistant.options.volatility;

import com.tradingassistant.options.exception.OptionsValidationException;
import com.tradingassistant.options.math.DecimalMath;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Bounded in-memory history of ATM implied volatility for one underlying.
 *
 * <p>The history is the only stateful component of the analytics layer beyond
 * the service's per-underlying registries. It is deliberately small, bounded by
 * the configured window and free of persistence. Observations must arrive in
 * non-decreasing time order; an observation with the same instant as the last
 * one replaces it, which makes replaying the same candle idempotent.</p>
 */
public final class ImpliedVolatilityHistory {

    private final int window;
    private final Deque<Observation> observations = new ArrayDeque<>();

    public ImpliedVolatilityHistory(int window) {
        if (window < 2) {
            throw new OptionsValidationException("volatility history window must be at least 2");
        }
        this.window = window;
    }

    public synchronized void record(Instant at, BigDecimal impliedVolatility) {
        Objects.requireNonNull(at, "at must not be null");
        Objects.requireNonNull(impliedVolatility, "impliedVolatility must not be null");
        if (impliedVolatility.signum() <= 0) {
            throw new OptionsValidationException("implied volatility must be positive");
        }
        Observation last = observations.peekLast();
        if (last != null) {
            if (at.isBefore(last.at())) {
                throw new OptionsValidationException(
                        "implied volatility observations must not move backwards in time");
            }
            if (at.equals(last.at())) {
                observations.pollLast();
            }
        }
        observations.addLast(new Observation(at, impliedVolatility));
        while (observations.size() > window) {
            observations.removeFirst();
        }
    }

    public synchronized Optional<BigDecimal> current() {
        Observation last = observations.peekLast();
        return last == null ? Optional.empty() : Optional.of(last.impliedVolatility());
    }

    public synchronized Optional<BigDecimal> change() {
        if (observations.size() < 2) {
            return Optional.empty();
        }
        List<Observation> values = new ArrayList<>(observations);
        return Optional.of(values.get(values.size() - 1).impliedVolatility()
                .subtract(values.get(values.size() - 2).impliedVolatility()));
    }

    /**
     * Position of the current value inside the observed range, as a percentage.
     * Empty when fewer than two observations exist or the range is flat.
     */
    public synchronized Optional<BigDecimal> rank() {
        if (observations.size() < 2) {
            return Optional.empty();
        }
        BigDecimal minimum = null;
        BigDecimal maximum = null;
        for (Observation observation : observations) {
            BigDecimal value = observation.impliedVolatility();
            minimum = minimum == null || value.compareTo(minimum) < 0 ? value : minimum;
            maximum = maximum == null || value.compareTo(maximum) > 0 ? value : maximum;
        }
        BigDecimal range = maximum.subtract(minimum);
        if (range.signum() == 0) {
            return Optional.empty();
        }
        return Optional.of(current().orElseThrow()
                .subtract(minimum)
                .divide(range, DecimalMath.CONTEXT)
                .multiply(DecimalMath.HUNDRED));
    }

    /**
     * Percentage of observations strictly below the current value. Empty when
     * fewer than two observations exist.
     */
    public synchronized Optional<BigDecimal> percentile() {
        if (observations.size() < 2) {
            return Optional.empty();
        }
        BigDecimal current = current().orElseThrow();
        long below = observations.stream()
                .filter(observation -> observation.impliedVolatility().compareTo(current) < 0)
                .count();
        return Optional.of(BigDecimal.valueOf(below)
                .multiply(DecimalMath.HUNDRED)
                .divide(BigDecimal.valueOf(observations.size()),
                        DecimalMath.CONTEXT));
    }

    public synchronized int size() {
        return observations.size();
    }

    public synchronized List<Observation> snapshot() {
        return List.copyOf(observations);
    }

    /**
     * One volatility observation.
     */
    public record Observation(Instant at, BigDecimal impliedVolatility) {

        public Observation {
            at = Objects.requireNonNull(at, "at must not be null");
            impliedVolatility = Objects.requireNonNull(impliedVolatility,
                    "impliedVolatility must not be null");
        }
    }
}

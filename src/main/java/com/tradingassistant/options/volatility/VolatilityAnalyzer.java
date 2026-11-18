package com.tradingassistant.options.volatility;

import com.tradingassistant.intelligence.domain.AssessmentStatus;
import com.tradingassistant.market.domain.OptionType;
import com.tradingassistant.options.config.OptionsAnalyticsConfig;
import com.tradingassistant.options.domain.ChainSnapshot;
import com.tradingassistant.options.domain.ExpiryChain;
import com.tradingassistant.options.domain.IvSurfacePoint;
import com.tradingassistant.options.domain.Moneyness;
import com.tradingassistant.options.domain.OptionLegKey;
import com.tradingassistant.options.domain.SmilePoint;
import com.tradingassistant.options.domain.Strike;
import com.tradingassistant.options.domain.StrikeGroup;
import com.tradingassistant.options.domain.VolatilityState;
import com.tradingassistant.options.domain.VolatilitySummary;
import com.tradingassistant.options.domain.VolatilityTrend;
import com.tradingassistant.options.math.DecimalMath;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Describes the implied-volatility state of the chain.
 *
 * <p>The ATM volatility is the mean of the solved ATM call and put volatilities.
 * The smile is the IV profile of the configured strike window around ATM; skew
 * is the mean out-of-the-money put volatility minus the mean out-of-the-money
 * call volatility of that window. Rank, percentile and change come from the
 * supplied history, which is updated with the current ATM observation after the
 * statistics are computed.</p>
 */
public final class VolatilityAnalyzer {

    private final OptionsAnalyticsConfig config;

    public VolatilityAnalyzer(OptionsAnalyticsConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    public VolatilitySummary analyze(ChainSnapshot snapshot,
                                     Map<OptionLegKey, BigDecimal> impliedVolatilities,
                                     ImpliedVolatilityHistory history) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        Objects.requireNonNull(impliedVolatilities, "impliedVolatilities must not be null");
        List<IvSurfacePoint> surface = surface(snapshot, impliedVolatilities);
        Optional<ExpiryChain> nearest = snapshot.nearestExpiry();
        if (nearest.isEmpty()) {
            return new VolatilitySummary(Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), VolatilityTrend.UNKNOWN,
                    VolatilityState.UNKNOWN, List.of(), surface,
                    AssessmentStatus.INSUFFICIENT_DATA, "chain has no expiry");
        }
        ExpiryChain expiry = nearest.get();
        Optional<StrikeGroup> atmGroup = expiry.atTheMoney();
        Optional<BigDecimal> atmCall = atmGroup.flatMap(group -> group.call())
                .flatMap(leg -> volatilityOf(leg, impliedVolatilities));
        Optional<BigDecimal> atmPut = atmGroup.flatMap(group -> group.put())
                .flatMap(leg -> volatilityOf(leg, impliedVolatilities));
        Optional<BigDecimal> atmVolatility = mean(atmCall, atmPut);

        List<SmilePoint> smile = smile(expiry, impliedVolatilities);
        Optional<BigDecimal> skew = skew(expiry, impliedVolatilities);

        Optional<BigDecimal> change = Optional.empty();
        Optional<BigDecimal> rank = Optional.empty();
        Optional<BigDecimal> percentile = Optional.empty();
        if (history != null) {
            change = history.change();
            rank = history.rank();
            percentile = history.percentile();
            atmVolatility.ifPresent(value -> history.record(snapshot.capturedAt(), value));
        }

        VolatilityTrend trend = trend(change);
        VolatilityState state = state(change);
        int priced = (int) snapshot.legs().stream().filter(leg -> leg.premium().isPresent()).count();
        int solved = impliedVolatilities.size();
        AssessmentStatus status;
        if (solved == 0) {
            status = AssessmentStatus.INSUFFICIENT_DATA;
        } else if (atmVolatility.isPresent() && solved >= priced) {
            status = AssessmentStatus.READY;
        } else {
            status = AssessmentStatus.PARTIAL;
        }
        String summary = "ATM IV " + atmVolatility.map(DecimalMath::format).orElse("unavailable")
                + " from " + solved + " of " + priced + " priced legs in "
                + expiry.expiry();
        return new VolatilitySummary(atmVolatility, change, rank, percentile, skew, trend, state,
                smile, surface, status, summary);
    }

    private List<IvSurfacePoint> surface(ChainSnapshot snapshot,
                                         Map<OptionLegKey, BigDecimal> volatilities) {
        List<IvSurfacePoint> points = new ArrayList<>();
        for (Strike leg : snapshot.legs()) {
            volatilityOf(leg, volatilities).ifPresent(volatility ->
                    points.add(new IvSurfacePoint(leg.expiry(), leg.strike(), leg.optionType(),
                            volatility)));
        }
        return List.copyOf(points);
    }

    private List<SmilePoint> smile(ExpiryChain expiry,
                                   Map<OptionLegKey, BigDecimal> volatilities) {
        List<StrikeGroup> groups = expiry.strikes();
        int atmIndex = atmIndex(expiry);
        int from = Math.max(0, atmIndex - config.smileStrikeRange());
        int to = Math.min(groups.size(), atmIndex + config.smileStrikeRange() + 1);
        List<SmilePoint> points = new ArrayList<>();
        for (int index = from; index < to; index++) {
            StrikeGroup group = groups.get(index);
            for (Strike leg : group.legs()) {
                volatilityOf(leg, volatilities).ifPresent(volatility ->
                        points.add(new SmilePoint(group.strike(), leg.optionType(),
                                leg.moneyness(), volatility)));
            }
        }
        points.sort(Comparator.comparing(SmilePoint::strike)
                .thenComparing(point -> point.optionType().isCall() ? 0 : 1));
        return List.copyOf(points);
    }

    private Optional<BigDecimal> skew(ExpiryChain expiry,
                                      Map<OptionLegKey, BigDecimal> volatilities) {
        List<StrikeGroup> groups = expiry.strikes();
        int atmIndex = atmIndex(expiry);
        int from = Math.max(0, atmIndex - config.smileStrikeRange());
        int to = Math.min(groups.size(), atmIndex + config.smileStrikeRange() + 1);
        BigDecimal putTotal = BigDecimal.ZERO;
        int putCount = 0;
        BigDecimal callTotal = BigDecimal.ZERO;
        int callCount = 0;
        for (int index = from; index < to; index++) {
            for (Strike leg : groups.get(index).legs()) {
                Optional<BigDecimal> volatility = volatilityOf(leg, volatilities);
                if (volatility.isEmpty() || !isOutOfTheMoney(leg)) {
                    continue;
                }
                if (leg.optionType() == OptionType.PUT) {
                    putTotal = putTotal.add(volatility.get());
                    putCount++;
                } else {
                    callTotal = callTotal.add(volatility.get());
                    callCount++;
                }
            }
        }
        if (putCount == 0 || callCount == 0) {
            return Optional.empty();
        }
        return Optional.of(putTotal.divide(BigDecimal.valueOf(putCount), DecimalMath.CONTEXT)
                .subtract(callTotal.divide(BigDecimal.valueOf(callCount),
                        DecimalMath.CONTEXT)));
    }

    private static boolean isOutOfTheMoney(Strike leg) {
        return leg.moneyness() == Moneyness.OTM || leg.moneyness() == Moneyness.DEEP_OTM;
    }

    private int atmIndex(ExpiryChain expiry) {
        List<StrikeGroup> groups = expiry.strikes();
        for (int index = 0; index < groups.size(); index++) {
            if (groups.get(index).atTheMoney()) {
                return index;
            }
        }
        return 0;
    }

    private Optional<BigDecimal> volatilityOf(Strike leg,
                                              Map<OptionLegKey, BigDecimal> volatilities) {
        return Optional.ofNullable(volatilities.get(OptionLegKey.of(leg)));
    }

    private static Optional<BigDecimal> mean(Optional<BigDecimal> first,
                                             Optional<BigDecimal> second) {
        if (first.isEmpty() && second.isEmpty()) {
            return Optional.empty();
        }
        if (first.isEmpty()) {
            return second;
        }
        if (second.isEmpty()) {
            return first;
        }
        return Optional.of(first.get().add(second.get())
                .divide(DecimalMath.TWO, DecimalMath.CONTEXT));
    }

    private VolatilityTrend trend(Optional<BigDecimal> change) {
        if (change.isEmpty()) {
            return VolatilityTrend.UNKNOWN;
        }
        BigDecimal value = change.get();
        if (value.compareTo(config.ivTrendThreshold()) > 0) {
            return VolatilityTrend.RISING;
        }
        if (value.negate().compareTo(config.ivTrendThreshold()) > 0) {
            return VolatilityTrend.FALLING;
        }
        return VolatilityTrend.FLAT;
    }

    private VolatilityState state(Optional<BigDecimal> change) {
        return switch (trend(change)) {
            case RISING -> VolatilityState.EXPANDING;
            case FALLING -> VolatilityState.CONTRACTING;
            case FLAT -> VolatilityState.STABLE;
            case UNKNOWN -> VolatilityState.UNKNOWN;
        };
    }
}

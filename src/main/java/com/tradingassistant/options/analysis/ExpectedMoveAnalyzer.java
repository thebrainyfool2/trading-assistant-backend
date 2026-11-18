package com.tradingassistant.options.analysis;

import com.tradingassistant.intelligence.domain.AssessmentStatus;
import com.tradingassistant.options.config.OptionsAnalyticsConfig;
import com.tradingassistant.options.domain.ChainSnapshot;
import com.tradingassistant.options.domain.ExpectedMoveMethod;
import com.tradingassistant.options.domain.ExpectedMoveSummary;
import com.tradingassistant.options.domain.ExpiryChain;
import com.tradingassistant.options.domain.OptionLegKey;
import com.tradingassistant.options.domain.Strike;
import com.tradingassistant.options.domain.StrikeGroup;
import com.tradingassistant.options.math.DecimalMath;
import com.tradingassistant.options.math.ExpiryTime;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Describes the expected move of the underlying.
 *
 * <p>The volatility method multiplies spot by ATM implied volatility and the
 * square root of the horizon. The straddle method uses the ATM call plus put
 * premium and scales it by the square root of the horizon. Both produce absolute
 * price distances, not directions.</p>
 */
public final class ExpectedMoveAnalyzer {

    private final OptionsAnalyticsConfig config;

    public ExpectedMoveAnalyzer(OptionsAnalyticsConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    public ExpectedMoveSummary analyze(ChainSnapshot snapshot,
                                       Map<OptionLegKey, BigDecimal> impliedVolatilities) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        Objects.requireNonNull(impliedVolatilities, "impliedVolatilities must not be null");
        Optional<ExpiryChain> nearest = snapshot.nearestExpiry();
        if (nearest.isEmpty()) {
            return unavailable(snapshot, AssessmentStatus.INSUFFICIENT_DATA,
                    "chain has no expiry");
        }
        ExpiryChain expiry = nearest.get();
        Optional<StrikeGroup> atmGroup = expiry.atTheMoney();
        if (atmGroup.isEmpty()) {
            return unavailable(snapshot, AssessmentStatus.INSUFFICIENT_DATA,
                    "chain has no at-the-money strike");
        }
        StrikeGroup group = atmGroup.get();
        BigDecimal years = ExpiryTime.yearsToExpiry(expiry.expiry(), snapshot.capturedAt(),
                config.exchangeZone(), config.dayCount());
        Optional<BigDecimal> atmVolatility = mean(
                volatility(group.call().orElse(null), impliedVolatilities),
                volatility(group.put().orElse(null), impliedVolatilities));

        Optional<BigDecimal> expiryMove;
        if (config.expectedMoveMethod() == ExpectedMoveMethod.VOLATILITY) {
            expiryMove = atmVolatility.map(volatility -> snapshot.spotPrice()
                    .multiply(volatility, DecimalMath.CONTEXT)
                    .multiply(DecimalMath.sqrt(years), DecimalMath.CONTEXT));
        } else {
            expiryMove = straddle(group);
        }

        Optional<BigDecimal> daily;
        Optional<BigDecimal> weekly;
        if (config.expectedMoveMethod() == ExpectedMoveMethod.VOLATILITY) {
            daily = atmVolatility.map(volatility -> snapshot.spotPrice()
                    .multiply(volatility, DecimalMath.CONTEXT)
                    .multiply(DecimalMath.sqrt(config.dailyYearFraction()),
                            DecimalMath.CONTEXT));
            weekly = atmVolatility.map(volatility -> snapshot.spotPrice()
                    .multiply(volatility, DecimalMath.CONTEXT)
                    .multiply(DecimalMath.sqrt(config.expectedMoveYearFraction()),
                            DecimalMath.CONTEXT));
        } else {
            daily = expiryMove.map(move -> move.multiply(scale(1L, expiry), DecimalMath.CONTEXT));
            weekly = expiryMove.map(move -> move.multiply(
                    scale(config.expectedMoveDays(), expiry), DecimalMath.CONTEXT));
        }

        Optional<BigDecimal> upper = expiryMove.map(move -> snapshot.spotPrice().add(move));
        Optional<BigDecimal> lower = expiryMove.map(move -> snapshot.spotPrice().subtract(move));
        AssessmentStatus status = expiryMove.isPresent()
                ? AssessmentStatus.READY
                : AssessmentStatus.PARTIAL;
        String summary = "expected move " + expiryMove.map(DecimalMath::format)
                .orElse("unavailable") + " for " + expiry.expiry() + " using "
                + config.expectedMoveMethod();
        return new ExpectedMoveSummary(config.expectedMoveMethod(), Optional.of(expiry.expiry()),
                expiryMove, daily, weekly, upper, lower, status, summary);
    }

    private ExpectedMoveSummary unavailable(ChainSnapshot snapshot, AssessmentStatus status,
                                            String summary) {
        return new ExpectedMoveSummary(config.expectedMoveMethod(), snapshot.nearestExpiryDate(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), status, summary);
    }

    private Optional<BigDecimal> straddle(StrikeGroup group) {
        Optional<BigDecimal> call = group.call().flatMap(Strike::premium);
        Optional<BigDecimal> put = group.put().flatMap(Strike::premium);
        if (call.isEmpty() || put.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(call.get().add(put.get()));
    }

    private static BigDecimal scale(long horizonDays, ExpiryChain expiry) {
        long expiryDays = Math.max(1L, expiry.daysToExpiry());
        long days = Math.min(horizonDays, expiryDays);
        return DecimalMath.sqrt(BigDecimal.valueOf(days)
                .divide(BigDecimal.valueOf(expiryDays), DecimalMath.CONTEXT));
    }

    private static Optional<BigDecimal> volatility(Strike leg,
                                                   Map<OptionLegKey, BigDecimal> volatilities) {
        if (leg == null) {
            return Optional.empty();
        }
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
}

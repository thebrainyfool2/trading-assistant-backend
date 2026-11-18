package com.tradingassistant.risk.math;

import com.tradingassistant.intelligence.domain.VolatilityState;
import com.tradingassistant.risk.config.RiskEngineConfig;
import com.tradingassistant.risk.domain.PositionSizing;
import com.tradingassistant.risk.domain.RiskContext;
import com.tradingassistant.risk.domain.RiskPlan;
import com.tradingassistant.risk.domain.StopCalculation;
import com.tradingassistant.risk.domain.StopMethodology;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Optional;

/**
 * Derives the risk plan once per assessment.
 *
 * <p>The calculator is pure and stateless: the same context and configuration
 * always produce the same plan. It never rounds in a way that depends on locale,
 * time or ordering, and it never invents a value — a missing ATR or price simply
 * yields a plan without a stop or a size, which the rules then report as
 * insufficient data.</p>
 */
public final class RiskCalculator {

    /** Widening factor applied when the market volatility is expanding. */
    public static final BigDecimal EXPANDING_FACTOR = new BigDecimal("1.25");

    /** Narrowing factor applied when the market volatility is contracting. */
    public static final BigDecimal CONTRACTING_FACTOR = new BigDecimal("0.85");

    /** Neutral factor for a stable or unknown volatility state. */
    public static final BigDecimal NEUTRAL_FACTOR = BigDecimal.ONE;

    /** Decimal places kept for a fractional quantity. */
    private static final int FRACTIONAL_SCALE = 4;

    /**
     * Volatility adjustment applied to the configured ATR multiple.
     *
     * <p>Expanding volatility widens the stop and contracting volatility narrows
     * it. The adjustment is deliberately bounded and deterministic, and it is
     * reported on the assessment so a consumer can see what was applied.</p>
     */
    public static BigDecimal volatilityAdjustment(VolatilityState state) {
        if (state == null) {
            return NEUTRAL_FACTOR;
        }
        return switch (state) {
            case EXPANDING -> EXPANDING_FACTOR;
            case CONTRACTING -> CONTRACTING_FACTOR;
            case STABLE, UNKNOWN -> NEUTRAL_FACTOR;
        };
    }

    /**
     * Derives the plan for one context.
     */
    public RiskPlan calculate(RiskContext context, RiskEngineConfig config) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(config, "config must not be null");

        BigDecimal capital = config.referenceCapital();
        BigDecimal riskPercent = config.riskPercentFor(context.instrument());
        BigDecimal riskAmount = RiskEngineConfig.percentOf(capital, riskPercent);
        BigDecimal adjustment = volatilityAdjustment(
                context.marketAssessment().volatility().state());
        BigDecimal atrMultiple = config.defaultATRMultiplier().multiply(adjustment);

        Optional<BigDecimal> atr = context.atr().filter(value -> value.signum() > 0);
        Optional<BigDecimal> price = context.price().filter(value -> value.signum() > 0);
        StopCalculation stop = deriveStop(atr, price, atrMultiple, adjustment);
        Optional<PositionSizing> sizing = deriveSizing(riskAmount, stop, price, capital, config);
        Optional<BigDecimal> availableReward = expectedMove(context);

        return new RiskPlan(capital, riskPercent, riskAmount, atr, adjustment, atrMultiple,
                stop, sizing, availableReward, config.allowFractionalQuantity());
    }

    private static StopCalculation deriveStop(Optional<BigDecimal> atr,
                                              Optional<BigDecimal> price,
                                              BigDecimal atrMultiple,
                                              BigDecimal adjustment) {
        if (atr.isEmpty() || price.isEmpty()) {
            return StopCalculation.none();
        }
        BigDecimal value = atr.get();
        BigDecimal distance = value.multiply(atrMultiple);
        BigDecimal distancePercent = RiskNumbers.percentOf(distance, price.get());
        return new StopCalculation(StopMethodology.ATR, value, atrMultiple, adjustment,
                distance, distancePercent);
    }

    private static Optional<PositionSizing> deriveSizing(BigDecimal riskAmount,
                                                         StopCalculation stop,
                                                         Optional<BigDecimal> price,
                                                         BigDecimal capital,
                                                         RiskEngineConfig config) {
        if (!stop.available() || price.isEmpty() || stop.distance().signum() <= 0) {
            return Optional.empty();
        }
        BigDecimal raw = riskAmount.divide(stop.distance(), MathContext.DECIMAL128);
        BigDecimal quantity = config.allowFractionalQuantity()
                ? raw.setScale(FRACTIONAL_SCALE, RoundingMode.HALF_UP)
                : raw.setScale(0, RoundingMode.DOWN);
        if (quantity.signum() <= 0) {
            return Optional.empty();
        }
        BigDecimal priceValue = price.get();
        BigDecimal positionValue = quantity.multiply(priceValue);
        BigDecimal requiredLeverage = positionValue.divide(capital, MathContext.DECIMAL128);
        return Optional.of(new PositionSizing(quantity, positionValue, requiredLeverage,
                priceValue, config.allowFractionalQuantity()));
    }

    private static Optional<BigDecimal> expectedMove(RiskContext context) {
        return context.optionsAssessment()
                .flatMap(options -> options.expectedMove().expiryMove()
                        .or(() -> options.expectedMove().dailyMove()))
                .map(BigDecimal::abs)
                .filter(value -> value.signum() > 0);
    }
}

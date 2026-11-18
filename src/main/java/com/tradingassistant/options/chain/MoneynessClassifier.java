package com.tradingassistant.options.chain;

import com.tradingassistant.market.domain.OptionType;
import com.tradingassistant.options.config.OptionsAnalyticsConfig;
import com.tradingassistant.options.domain.Moneyness;
import com.tradingassistant.options.math.DecimalMath;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Classifies a strike against the spot price.
 *
 * <p>The ATM flag is decided by the chain builder, because an ATM band can be
 * empty and then falls back to the nearest strike. Every other class is pure
 * geometry: distance from spot, option type and the configured deep thresholds.</p>
 */
public final class MoneynessClassifier {

    private final OptionsAnalyticsConfig config;

    public MoneynessClassifier(OptionsAnalyticsConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    public Moneyness classify(BigDecimal spot, BigDecimal strike, OptionType type, boolean atTheMoney) {
        Objects.requireNonNull(spot, "spot must not be null");
        Objects.requireNonNull(strike, "strike must not be null");
        Objects.requireNonNull(type, "type must not be null");
        if (spot.signum() <= 0) {
            throw new IllegalArgumentException("spot must be positive");
        }
        if (atTheMoney) {
            return Moneyness.ATM;
        }
        BigDecimal distancePercent = distancePercent(spot, strike);
        boolean inTheMoney = type.isCall()
                ? strike.compareTo(spot) < 0
                : strike.compareTo(spot) > 0;
        BigDecimal threshold = inTheMoney
                ? config.deepItmDistancePercent()
                : config.deepOtmDistancePercent();
        boolean deep = distancePercent.compareTo(threshold) >= 0;
        if (inTheMoney) {
            return deep ? Moneyness.DEEP_ITM : Moneyness.ITM;
        }
        return deep ? Moneyness.DEEP_OTM : Moneyness.OTM;
    }

    /**
     * True when the strike lies inside the configured ATM band.
     */
    public boolean withinAtmBand(BigDecimal spot, BigDecimal strike) {
        return distancePercent(spot, strike).compareTo(config.atmDistancePercent()) <= 0;
    }

    public BigDecimal distancePercent(BigDecimal spot, BigDecimal strike) {
        return strike.subtract(spot).abs()
                .divide(spot, DecimalMath.CONTEXT)
                .multiply(DecimalMath.HUNDRED, DecimalMath.CONTEXT);
    }
}

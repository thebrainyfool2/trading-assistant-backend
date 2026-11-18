package com.tradingassistant.indicator.calculator;

import com.tradingassistant.indicator.config.IndicatorConfig;
import com.tradingassistant.indicator.config.PriceSource;
import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.indicator.domain.IndicatorValues;
import com.tradingassistant.indicator.util.CandleMath;
import com.tradingassistant.indicator.util.IndicatorMath;
import com.tradingassistant.market.domain.Candle;

import java.math.BigDecimal;
import java.util.Map;

public final class SupertrendIndicator extends AbstractIndicator {

    public static final String TYPE = "SUPERTREND";
    private final AtrIndicator atr;
    private BigDecimal previousClose;
    private BigDecimal previousFinalUpper;
    private BigDecimal previousFinalLower;
    private BigDecimal previousSupertrend;
    private BigDecimal direction;

    public SupertrendIndicator(IndicatorConfig config) {
        super(TYPE, config);
        this.atr = new AtrIndicator(IndicatorConfig.period(config.period(), PriceSource.CLOSE));
    }

    @Override
    public int minimumCandles() {
        return atr.minimumCandles();
    }

    @Override
    protected IndicatorResult compute(Candle candle) {
        IndicatorResult atrResult = atr.update(candle);
        BigDecimal priorClose = previousClose;
        previousClose = candle.close();
        if (!atrResult.hasValues()) {
            return warming(candle);
        }

        BigDecimal atrValue = atrResult.requiredValue(IndicatorValues.VALUE);
        BigDecimal midpoint = CandleMath.midpoint(candle);
        BigDecimal basicUpper = midpoint.add(atrValue.multiply(config.multiplier()));
        BigDecimal basicLower = midpoint.subtract(atrValue.multiply(config.multiplier()));

        BigDecimal finalUpper = basicUpper;
        BigDecimal finalLower = basicLower;
        if (previousFinalUpper != null) {
            if (basicUpper.compareTo(previousFinalUpper) >= 0
                    && priorClose.compareTo(previousFinalUpper) <= 0) {
                finalUpper = previousFinalUpper;
            }
            if (basicLower.compareTo(previousFinalLower) <= 0
                    && priorClose.compareTo(previousFinalLower) >= 0) {
                finalLower = previousFinalLower;
            }
        }

        BigDecimal supertrend;
        if (previousSupertrend == null) {
            supertrend = candle.close().compareTo(basicLower) >= 0 ? basicLower : basicUpper;
        } else if (previousSupertrend.compareTo(previousFinalUpper) == 0) {
            supertrend = candle.close().compareTo(finalUpper) <= 0 ? finalUpper : finalLower;
        } else {
            supertrend = candle.close().compareTo(finalLower) >= 0 ? finalLower : finalUpper;
        }

        direction = candle.close().compareTo(supertrend) >= 0
                ? BigDecimal.ONE : BigDecimal.ONE.negate();
        previousFinalUpper = finalUpper;
        previousFinalLower = finalLower;
        previousSupertrend = supertrend;

        return result(candle, Map.of(
                IndicatorValues.SUPERTREND, supertrend,
                IndicatorValues.DIRECTION, direction));
    }

    @Override
    protected void onReset() {
        atr.reset();
        previousClose = null;
        previousFinalUpper = null;
        previousFinalLower = null;
        previousSupertrend = null;
        direction = null;
    }
}

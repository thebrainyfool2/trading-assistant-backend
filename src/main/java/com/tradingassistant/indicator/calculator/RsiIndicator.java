package com.tradingassistant.indicator.calculator;

import com.tradingassistant.indicator.config.IndicatorConfig;
import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.indicator.domain.IndicatorValues;
import com.tradingassistant.indicator.util.CandleMath;
import com.tradingassistant.indicator.util.IndicatorMath;
import com.tradingassistant.market.domain.Candle;

import java.math.BigDecimal;
import java.util.Map;

public final class RsiIndicator extends AbstractIndicator {

    public static final String TYPE = "RSI";
    private BigDecimal previousClose;
    private BigDecimal gainSum = BigDecimal.ZERO;
    private BigDecimal lossSum = BigDecimal.ZERO;
    private BigDecimal averageGain;
    private BigDecimal averageLoss;
    private int changes;

    public RsiIndicator(IndicatorConfig config) {
        super(TYPE, config);
    }

    @Override
    public int minimumCandles() {
        return config.period() + 1;
    }

    @Override
    protected IndicatorResult compute(Candle candle) {
        if (previousClose == null) {
            previousClose = candle.close();
            return warming(candle);
        }

        BigDecimal change = candle.close().subtract(previousClose);
        previousClose = candle.close();
        BigDecimal gain = change.signum() > 0 ? change : BigDecimal.ZERO;
        BigDecimal loss = change.signum() < 0 ? change.negate() : BigDecimal.ZERO;
        changes++;

        if (changes <= config.period()) {
            gainSum = gainSum.add(gain);
            lossSum = lossSum.add(loss);
            if (changes < config.period()) {
                return warming(candle);
            }
            averageGain = IndicatorMath.divide(gainSum, BigDecimal.valueOf(config.period()));
            averageLoss = IndicatorMath.divide(lossSum, BigDecimal.valueOf(config.period()));
        } else {
            averageGain = CandleMath.wilderAverage(averageGain, gain, config.period());
            averageLoss = CandleMath.wilderAverage(averageLoss, loss, config.period());
        }

        BigDecimal value;
        if (averageLoss.signum() == 0) {
            value = averageGain.signum() == 0
                    ? BigDecimal.valueOf(50)
                    : IndicatorMath.ONE_HUNDRED;
        } else {
            BigDecimal relativeStrength = IndicatorMath.divide(averageGain, averageLoss);
            value = IndicatorMath.ONE_HUNDRED.subtract(
                    IndicatorMath.divide(IndicatorMath.ONE_HUNDRED,
                            BigDecimal.ONE.add(relativeStrength)));
        }
        return result(candle, Map.of(IndicatorValues.VALUE, value));
    }

    @Override
    protected void onReset() {
        previousClose = null;
        gainSum = BigDecimal.ZERO;
        lossSum = BigDecimal.ZERO;
        averageGain = null;
        averageLoss = null;
        changes = 0;
    }
}

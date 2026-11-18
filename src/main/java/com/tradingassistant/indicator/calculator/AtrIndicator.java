package com.tradingassistant.indicator.calculator;

import com.tradingassistant.indicator.config.IndicatorConfig;
import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.indicator.domain.IndicatorValues;
import com.tradingassistant.indicator.util.CandleMath;
import com.tradingassistant.indicator.util.IndicatorMath;
import com.tradingassistant.market.domain.Candle;

import java.math.BigDecimal;
import java.util.Map;

public final class AtrIndicator extends AbstractIndicator {

    public static final String TYPE = "ATR";
    private BigDecimal previousClose;
    private BigDecimal trueRangeSum = BigDecimal.ZERO;
    private BigDecimal atr;
    private int trueRangeCount;

    public AtrIndicator(IndicatorConfig config) {
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

        BigDecimal trueRange = CandleMath.trueRange(candle, previousClose);
        previousClose = candle.close();
        trueRangeCount++;

        if (trueRangeCount <= config.period()) {
            trueRangeSum = trueRangeSum.add(trueRange);
            if (trueRangeCount < config.period()) {
                return warming(candle);
            }
            atr = IndicatorMath.divide(trueRangeSum, BigDecimal.valueOf(config.period()));
        } else {
            atr = CandleMath.wilderAverage(atr, trueRange, config.period());
        }

        return result(candle, Map.of(IndicatorValues.VALUE, atr));
    }

    @Override
    protected void onReset() {
        previousClose = null;
        trueRangeSum = BigDecimal.ZERO;
        atr = null;
        trueRangeCount = 0;
    }
}

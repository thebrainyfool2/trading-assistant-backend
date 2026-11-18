package com.tradingassistant.indicator.calculator;

import com.tradingassistant.indicator.config.IndicatorConfig;
import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.indicator.domain.IndicatorValues;
import com.tradingassistant.indicator.util.IndicatorMath;
import com.tradingassistant.market.domain.Candle;

import java.math.BigDecimal;
import java.util.Map;

public final class EmaIndicator extends AbstractIndicator {

    public static final String TYPE = "EMA";
    private final BigDecimal alpha;
    private final BigDecimal oneMinusAlpha;
    private BigDecimal ema;
    private int count;

    public EmaIndicator(IndicatorConfig config) {
        super(TYPE, config);
        this.alpha = IndicatorMath.divide(
                BigDecimal.valueOf(2), BigDecimal.valueOf(config.period() + 1L));
        this.oneMinusAlpha = BigDecimal.ONE.subtract(alpha);
    }

    @Override
    public int minimumCandles() {
        return config.period();
    }

    @Override
    protected IndicatorResult compute(Candle candle) {
        BigDecimal current = source(candle);
        count++;
        if (count == 1) {
            ema = current;
        } else {
            ema = current.multiply(alpha).add(ema.multiply(oneMinusAlpha));
        }
        if (count < config.period()) {
            return warming(candle);
        }
        return result(candle, Map.of(IndicatorValues.VALUE, ema));
    }

    @Override
    protected void onReset() {
        ema = null;
        count = 0;
    }
}

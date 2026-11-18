package com.tradingassistant.indicator.calculator;

import com.tradingassistant.indicator.config.IndicatorConfig;
import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.indicator.domain.IndicatorValues;
import com.tradingassistant.indicator.util.IndicatorMath;
import com.tradingassistant.indicator.util.RollingWindow;
import com.tradingassistant.market.domain.Candle;

import java.math.BigDecimal;
import java.util.Map;

public final class SmaIndicator extends AbstractIndicator {

    public static final String TYPE = "SMA";
    private final RollingWindow window;

    public SmaIndicator(IndicatorConfig config) {
        super(TYPE, config);
        this.window = new RollingWindow(config.period());
    }

    @Override
    public int minimumCandles() {
        return config.period();
    }

    @Override
    protected IndicatorResult compute(Candle candle) {
        window.add(source(candle));
        if (!window.isFull()) {
            return warming(candle);
        }
        BigDecimal value = IndicatorMath.divide(window.sum(), BigDecimal.valueOf(config.period()));
        return result(candle, Map.of(IndicatorValues.VALUE, value));
    }

    @Override
    protected void onReset() {
        window.clear();
    }
}

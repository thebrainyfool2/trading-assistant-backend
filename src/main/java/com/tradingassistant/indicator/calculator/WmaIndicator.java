package com.tradingassistant.indicator.calculator;

import com.tradingassistant.indicator.config.IndicatorConfig;
import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.indicator.domain.IndicatorValues;
import com.tradingassistant.indicator.util.IndicatorMath;
import com.tradingassistant.indicator.util.RollingWindow;
import com.tradingassistant.market.domain.Candle;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class WmaIndicator extends AbstractIndicator {

    public static final String TYPE = "WMA";
    private final RollingWindow window;
    private final BigDecimal denominator;

    public WmaIndicator(IndicatorConfig config) {
        super(TYPE, config);
        this.window = new RollingWindow(config.period());
        long period = config.period();
        this.denominator = BigDecimal.valueOf(period * (period + 1) / 2);
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
        List<BigDecimal> values = window.values();
        BigDecimal weighted = BigDecimal.ZERO;
        for (int i = 0; i < values.size(); i++) {
            weighted = weighted.add(values.get(i).multiply(BigDecimal.valueOf(i + 1L)));
        }
        return result(candle, Map.of(IndicatorValues.VALUE,
                IndicatorMath.divide(weighted, denominator)));
    }

    @Override
    protected void onReset() {
        window.clear();
    }
}

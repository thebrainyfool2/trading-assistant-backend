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

public final class BollingerBandsIndicator extends AbstractIndicator {

    public static final String TYPE = "BOLLINGER";
    private final RollingWindow window;

    public BollingerBandsIndicator(IndicatorConfig config) {
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

        List<BigDecimal> values = window.values();
        BigDecimal middle = IndicatorMath.divide(window.sum(), BigDecimal.valueOf(config.period()));
        BigDecimal squaredDeviations = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            BigDecimal difference = value.subtract(middle);
            squaredDeviations = squaredDeviations.add(difference.multiply(difference));
        }
        BigDecimal variance = IndicatorMath.divide(
                squaredDeviations, BigDecimal.valueOf(config.period()));
        BigDecimal deviation = IndicatorMath.sqrt(variance);
        BigDecimal width = deviation.multiply(config.deviation());

        return result(candle, Map.of(
                IndicatorValues.UPPER, middle.add(width),
                IndicatorValues.MIDDLE, middle,
                IndicatorValues.LOWER, middle.subtract(width)));
    }

    @Override
    protected void onReset() {
        window.clear();
    }
}

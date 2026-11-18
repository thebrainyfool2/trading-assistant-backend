package com.tradingassistant.indicator.calculator;

import com.tradingassistant.indicator.config.IndicatorConfig;
import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.indicator.domain.IndicatorValues;
import com.tradingassistant.market.domain.Candle;

import java.util.Map;

public final class DmiIndicator extends AbstractIndicator {

    public static final String TYPE = "DMI";
    private final DirectionalMovementCalculator movement;

    public DmiIndicator(IndicatorConfig config) {
        super(TYPE, config);
        this.movement = new DirectionalMovementCalculator(config.period());
    }

    @Override
    public int minimumCandles() {
        return config.period() + 1;
    }

    @Override
    protected IndicatorResult compute(Candle candle) {
        movement.update(candle);
        if (!movement.hasDi()) {
            return warming(candle);
        }
        return result(candle, Map.of(
                IndicatorValues.PLUS_DI, movement.plusDi(),
                IndicatorValues.MINUS_DI, movement.minusDi()));
    }

    @Override
    protected void onReset() {
        movement.reset();
    }
}

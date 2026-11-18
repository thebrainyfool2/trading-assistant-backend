package com.tradingassistant.indicator.calculator;

import com.tradingassistant.indicator.config.IndicatorConfig;
import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.indicator.domain.IndicatorValues;
import com.tradingassistant.market.domain.Candle;

import java.util.Map;

public final class AdxIndicator extends AbstractIndicator {

    public static final String TYPE = "ADX";
    private final DirectionalMovementCalculator movement;

    public AdxIndicator(IndicatorConfig config) {
        super(TYPE, config);
        this.movement = new DirectionalMovementCalculator(config.period());
    }

    @Override
    public int minimumCandles() {
        return 2 * config.period();
    }

    @Override
    protected IndicatorResult compute(Candle candle) {
        movement.update(candle);
        if (!movement.hasAdx()) {
            return warming(candle);
        }
        return result(candle, Map.of(IndicatorValues.ADX, movement.adx()));
    }

    @Override
    protected void onReset() {
        movement.reset();
    }
}

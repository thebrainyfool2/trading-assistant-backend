package com.tradingassistant.indicator.calculator;

import com.tradingassistant.indicator.config.IndicatorConfig;
import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.indicator.domain.IndicatorValues;
import com.tradingassistant.indicator.util.CandleMath;
import com.tradingassistant.indicator.util.IndicatorMath;
import com.tradingassistant.indicator.util.RollingWindow;
import com.tradingassistant.market.domain.Candle;

import java.math.BigDecimal;
import java.util.Map;

public final class CmfIndicator extends AbstractIndicator {

    public static final String TYPE = "CMF";
    private final RollingWindow moneyFlow = new RollingWindow(config.period());
    private final RollingWindow volume = new RollingWindow(config.period());

    public CmfIndicator(IndicatorConfig config) {
        super(TYPE, config);
    }

    @Override
    public int minimumCandles() {
        return config.period();
    }

    @Override
    protected IndicatorResult compute(Candle candle) {
        BigDecimal candleVolume = BigDecimal.valueOf(candle.volume());
        BigDecimal flow = CandleMath.moneyFlowMultiplier(candle).multiply(candleVolume);
        moneyFlow.add(flow);
        volume.add(candleVolume);
        if (!moneyFlow.isFull()) {
            return warming(candle);
        }

        BigDecimal value = volume.sum().signum() == 0
                ? BigDecimal.ZERO
                : IndicatorMath.divide(moneyFlow.sum(), volume.sum());
        return result(candle, Map.of(IndicatorValues.VALUE, value));
    }

    @Override
    protected void onReset() {
        moneyFlow.clear();
        volume.clear();
    }
}

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

public final class StochasticRsiIndicator extends AbstractIndicator {

    public static final String TYPE = "STOCH_RSI";
    private final RsiIndicator rsi;
    private final RollingWindow rsiWindow;
    private final RollingWindow kWindow;
    private final RollingWindow dWindow;

    public StochasticRsiIndicator(IndicatorConfig config) {
        super(TYPE, config);
        this.rsi = new RsiIndicator(IndicatorConfig.period(config.period(), config.source()));
        this.rsiWindow = new RollingWindow(config.period());
        this.kWindow = new RollingWindow(config.smoothing());
        this.dWindow = new RollingWindow(config.smoothing());
    }

    @Override
    public int minimumCandles() {
        return rsi.minimumCandles()
                + config.period() - 1
                + 2 * (config.smoothing() - 1);
    }

    @Override
    protected IndicatorResult compute(Candle candle) {
        IndicatorResult rsiResult = rsi.update(candle);
        if (!rsiResult.hasValues()) {
            return warming(candle);
        }

        rsiWindow.add(rsiResult.requiredValue(IndicatorValues.VALUE));
        if (!rsiWindow.isFull()) {
            return warming(candle);
        }

        List<BigDecimal> rsiValues = rsiWindow.values();
        BigDecimal min = IndicatorMath.min(rsiValues);
        BigDecimal max = IndicatorMath.max(rsiValues);
        BigDecimal rawK = max.compareTo(min) == 0
                ? BigDecimal.valueOf(50)
                : IndicatorMath.divide(
                        rsiValues.getLast().subtract(min), max.subtract(min))
                        .multiply(IndicatorMath.ONE_HUNDRED);

        kWindow.add(rawK);
        if (!kWindow.isFull()) {
            return warming(candle);
        }
        BigDecimal k = IndicatorMath.divide(
                kWindow.sum(), BigDecimal.valueOf(config.smoothing()));

        dWindow.add(k);
        if (!dWindow.isFull()) {
            return warming(candle);
        }
        BigDecimal d = IndicatorMath.divide(
                dWindow.sum(), BigDecimal.valueOf(config.smoothing()));

        return result(candle, Map.of(
                IndicatorValues.PERCENT_K, k,
                IndicatorValues.PERCENT_D, d));
    }

    @Override
    protected void onReset() {
        rsi.reset();
        rsiWindow.clear();
        kWindow.clear();
        dWindow.clear();
    }
}

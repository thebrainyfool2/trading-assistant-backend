package com.tradingassistant.indicator.calculator;

import com.tradingassistant.indicator.config.IndicatorConfig;
import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.indicator.domain.IndicatorValues;
import com.tradingassistant.indicator.util.IndicatorMath;
import com.tradingassistant.market.domain.Candle;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

public final class DonchianChannelsIndicator extends AbstractIndicator {

    public static final String TYPE = "DONCHIAN";
    private final Deque<Candle> candles = new ArrayDeque<>();

    public DonchianChannelsIndicator(IndicatorConfig config) {
        super(TYPE, config);
    }

    @Override
    public int minimumCandles() {
        return config.period();
    }

    @Override
    protected IndicatorResult compute(Candle candle) {
        candles.addLast(candle);
        if (candles.size() > config.period()) {
            candles.removeFirst();
        }
        if (candles.size() < config.period()) {
            return warming(candle);
        }

        BigDecimal highest = null;
        BigDecimal lowest = null;
        for (Candle item : candles) {
            highest = highest == null ? item.high() : highest.max(item.high());
            lowest = lowest == null ? item.low() : lowest.min(item.low());
        }
        BigDecimal middle = IndicatorMath.divide(highest.add(lowest), BigDecimal.valueOf(2));
        return result(candle, Map.of(
                IndicatorValues.UPPER, highest,
                IndicatorValues.MIDDLE, middle,
                IndicatorValues.LOWER, lowest));
    }

    @Override
    protected void onReset() {
        candles.clear();
    }
}

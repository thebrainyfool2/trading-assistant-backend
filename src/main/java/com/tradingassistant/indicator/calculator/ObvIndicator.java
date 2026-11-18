package com.tradingassistant.indicator.calculator;

import com.tradingassistant.indicator.config.IndicatorConfig;
import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.indicator.domain.IndicatorValues;
import com.tradingassistant.market.domain.Candle;

import java.math.BigDecimal;
import java.util.Map;

public final class ObvIndicator extends AbstractIndicator {

    public static final String TYPE = "OBV";
    private BigDecimal previousClose;
    private BigDecimal obv = BigDecimal.ZERO;

    public ObvIndicator(IndicatorConfig config) {
        super(TYPE, config);
    }

    @Override
    public int minimumCandles() {
        return 1;
    }

    @Override
    protected IndicatorResult compute(Candle candle) {
        if (previousClose == null) {
            previousClose = candle.close();
            return result(candle, Map.of(IndicatorValues.VALUE, obv));
        }

        int comparison = candle.close().compareTo(previousClose);
        if (comparison > 0) {
            obv = obv.add(BigDecimal.valueOf(candle.volume()));
        } else if (comparison < 0) {
            obv = obv.subtract(BigDecimal.valueOf(candle.volume()));
        }
        previousClose = candle.close();
        return result(candle, Map.of(IndicatorValues.VALUE, obv));
    }

    @Override
    protected void onReset() {
        previousClose = null;
        obv = BigDecimal.ZERO;
    }
}

package com.tradingassistant.indicator.calculator;

import com.tradingassistant.indicator.config.IndicatorConfig;
import com.tradingassistant.indicator.config.PriceSource;
import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.indicator.domain.IndicatorValues;
import com.tradingassistant.indicator.util.CandleMath;
import com.tradingassistant.indicator.util.IndicatorMath;
import com.tradingassistant.market.domain.Candle;

import java.math.BigDecimal;
import java.util.Map;

public final class KeltnerChannelsIndicator extends AbstractIndicator {

    public static final String TYPE = "KELTNER";
    private final AtrIndicator atr;
    private final BigDecimal alpha;
    private final BigDecimal oneMinusAlpha;
    private BigDecimal middle;
    private int count;

    public KeltnerChannelsIndicator(IndicatorConfig config) {
        super(TYPE, config);
        this.atr = new AtrIndicator(IndicatorConfig.period(config.smoothing(), PriceSource.CLOSE));
        this.alpha = IndicatorMath.divide(
                BigDecimal.valueOf(2), BigDecimal.valueOf(config.period() + 1L));
        this.oneMinusAlpha = BigDecimal.ONE.subtract(alpha);
    }

    @Override
    public int minimumCandles() {
        return Math.max(config.period(), atr.minimumCandles());
    }

    @Override
    protected IndicatorResult compute(Candle candle) {
        BigDecimal typical = CandleMath.typicalPrice(candle);
        count++;
        middle = count == 1
                ? typical
                : typical.multiply(alpha).add(middle.multiply(oneMinusAlpha));

        IndicatorResult atrResult = atr.update(candle);
        if (!atrResult.hasValues() || count < config.period()) {
            return warming(candle);
        }

        BigDecimal width = atrResult.requiredValue(IndicatorValues.VALUE)
                .multiply(config.multiplier());
        return result(candle, Map.of(
                IndicatorValues.UPPER, middle.add(width),
                IndicatorValues.MIDDLE, middle,
                IndicatorValues.LOWER, middle.subtract(width)));
    }

    @Override
    protected void onReset() {
        atr.reset();
        middle = null;
        count = 0;
    }
}

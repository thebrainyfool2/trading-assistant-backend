package com.tradingassistant.indicator.calculator;

import com.tradingassistant.indicator.config.IndicatorConfig;
import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.indicator.domain.IndicatorValues;
import com.tradingassistant.indicator.exception.IndicatorValidationException;
import com.tradingassistant.indicator.util.IndicatorMath;
import com.tradingassistant.market.domain.Candle;

import java.math.BigDecimal;
import java.util.Map;

public final class MacdIndicator extends AbstractIndicator {

    public static final String TYPE = "MACD";
    private final BigDecimal fastAlpha;
    private final BigDecimal slowAlpha;
    private final BigDecimal signalAlpha;
    private BigDecimal fastEma;
    private BigDecimal slowEma;
    private BigDecimal signalEma;
    private int count;
    private int macdCount;

    public MacdIndicator(IndicatorConfig config) {
        super(TYPE, config);
        if (config.fastPeriod() >= config.slowPeriod()) {
            throw new IndicatorValidationException("MACD fastPeriod must be less than slowPeriod.");
        }
        this.fastAlpha = alpha(config.fastPeriod());
        this.slowAlpha = alpha(config.slowPeriod());
        this.signalAlpha = alpha(config.signalPeriod());
    }

    @Override
    public int minimumCandles() {
        return config.slowPeriod() + config.signalPeriod() - 1;
    }

    @Override
    protected IndicatorResult compute(Candle candle) {
        BigDecimal current = source(candle);
        count++;
        fastEma = count == 1 ? current : ema(current, fastEma, fastAlpha);
        slowEma = count == 1 ? current : ema(current, slowEma, slowAlpha);
        if (count < config.slowPeriod()) {
            return warming(candle);
        }

        BigDecimal macd = fastEma.subtract(slowEma);
        if (macdCount == 0) {
            signalEma = macd;
        } else {
            signalEma = ema(macd, signalEma, signalAlpha);
        }
        macdCount++;
        if (macdCount < config.signalPeriod()) {
            return warming(candle);
        }

        return result(candle, Map.of(
                IndicatorValues.MACD, macd,
                IndicatorValues.SIGNAL, signalEma,
                IndicatorValues.HISTOGRAM, macd.subtract(signalEma)));
    }

    private static BigDecimal alpha(int period) {
        return IndicatorMath.divide(BigDecimal.valueOf(2), BigDecimal.valueOf(period + 1L));
    }

    private static BigDecimal ema(BigDecimal current, BigDecimal previous, BigDecimal alpha) {
        return current.multiply(alpha)
                .add(previous.multiply(BigDecimal.ONE.subtract(alpha)));
    }

    @Override
    protected void onReset() {
        fastEma = null;
        slowEma = null;
        signalEma = null;
        count = 0;
        macdCount = 0;
    }
}

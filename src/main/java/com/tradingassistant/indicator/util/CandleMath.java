package com.tradingassistant.indicator.util;

import com.tradingassistant.market.domain.Candle;

import java.math.BigDecimal;

/**
 * Candle-derived values used by more than one indicator. All methods are pure
 * and do not retain state.
 */
public final class CandleMath {

    private CandleMath() {
    }

    public static BigDecimal typicalPrice(Candle candle) {
        return IndicatorMath.divide(
                candle.high().add(candle.low()).add(candle.close()),
                BigDecimal.valueOf(3));
    }

    public static BigDecimal midpoint(Candle candle) {
        return IndicatorMath.divide(candle.high().add(candle.low()), BigDecimal.valueOf(2));
    }

    public static BigDecimal trueRange(Candle current, BigDecimal previousClose) {
        BigDecimal highLow = current.high().subtract(current.low());
        if (previousClose == null) {
            return highLow;
        }
        BigDecimal highPrevious = current.high().subtract(previousClose).abs();
        BigDecimal lowPrevious = current.low().subtract(previousClose).abs();
        return highLow.max(highPrevious).max(lowPrevious);
    }

    public static BigDecimal moneyFlowMultiplier(Candle candle) {
        BigDecimal range = candle.high().subtract(candle.low());
        if (range.signum() == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal numerator = candle.close().subtract(candle.low())
                .subtract(candle.high().subtract(candle.close()));
        return IndicatorMath.divide(numerator, range);
    }

    public static BigDecimal wilderAverage(BigDecimal previousAverage, BigDecimal value, int period) {
        return previousAverage
                .multiply(BigDecimal.valueOf(period - 1L))
                .add(value)
                .divide(BigDecimal.valueOf(period), IndicatorMath.DIVISION_CONTEXT);
    }
}

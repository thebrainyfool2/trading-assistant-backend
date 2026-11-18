package com.tradingassistant.indicator.calculator;

import com.tradingassistant.indicator.util.CandleMath;
import com.tradingassistant.indicator.util.IndicatorMath;
import com.tradingassistant.market.domain.Candle;

import java.math.BigDecimal;

/**
 * Shared Wilder directional-movement state for DMI and ADX. The first DI is
 * available after period directional observations; ADX becomes available after
 * period DX observations.
 */
final class DirectionalMovementCalculator {

    private final int period;
    private BigDecimal previousHigh;
    private BigDecimal previousLow;
    private BigDecimal previousClose;
    private BigDecimal trSum = BigDecimal.ZERO;
    private BigDecimal plusDmSum = BigDecimal.ZERO;
    private BigDecimal minusDmSum = BigDecimal.ZERO;
    private BigDecimal smoothedTr;
    private BigDecimal smoothedPlusDm;
    private BigDecimal smoothedMinusDm;
    private BigDecimal plusDi;
    private BigDecimal minusDi;
    private BigDecimal dxSum = BigDecimal.ZERO;
    private int dxCount;
    private BigDecimal adx;
    private int changes;

    DirectionalMovementCalculator(int period) {
        this.period = period;
    }

    void update(Candle candle) {
        if (previousClose == null) {
            previousHigh = candle.high();
            previousLow = candle.low();
            previousClose = candle.close();
            return;
        }

        BigDecimal trueRange = CandleMath.trueRange(candle, previousClose);
        BigDecimal upMove = candle.high().subtract(previousHigh);
        BigDecimal downMove = previousLow.subtract(candle.low());
        BigDecimal plusDm = upMove.signum() > 0 && upMove.compareTo(downMove) > 0
                ? upMove : BigDecimal.ZERO;
        BigDecimal minusDm = downMove.signum() > 0 && downMove.compareTo(upMove) > 0
                ? downMove : BigDecimal.ZERO;

        changes++;
        if (changes <= period) {
            trSum = trSum.add(trueRange);
            plusDmSum = plusDmSum.add(plusDm);
            minusDmSum = minusDmSum.add(minusDm);
            if (changes < period) {
                previousHigh = candle.high();
                previousLow = candle.low();
                previousClose = candle.close();
                return;
            }
            smoothedTr = trSum;
            smoothedPlusDm = plusDmSum;
            smoothedMinusDm = minusDmSum;
        } else {
            smoothedTr = CandleMath.wilderAverage(smoothedTr, trueRange, period);
            smoothedPlusDm = CandleMath.wilderAverage(smoothedPlusDm, plusDm, period);
            smoothedMinusDm = CandleMath.wilderAverage(smoothedMinusDm, minusDm, period);
        }

        plusDi = percent(smoothedPlusDm, smoothedTr);
        minusDi = percent(smoothedMinusDm, smoothedTr);
        BigDecimal denominator = plusDi.add(minusDi);
        BigDecimal dx = denominator.signum() == 0
                ? BigDecimal.ZERO
                : IndicatorMath.divide(plusDi.subtract(minusDi).abs(), denominator)
                        .multiply(IndicatorMath.ONE_HUNDRED);

        dxSum = dxSum.add(dx);
        dxCount++;
        if (dxCount <= period) {
            if (dxCount == period) {
                adx = IndicatorMath.divide(dxSum, BigDecimal.valueOf(period));
            }
        } else {
            adx = CandleMath.wilderAverage(adx, dx, period);
        }

        previousHigh = candle.high();
        previousLow = candle.low();
        previousClose = candle.close();
    }

    private static BigDecimal percent(BigDecimal numerator, BigDecimal denominator) {
        return denominator.signum() == 0
                ? BigDecimal.ZERO
                : IndicatorMath.divide(numerator, denominator).multiply(IndicatorMath.ONE_HUNDRED);
    }

    boolean hasDi() {
        return plusDi != null && minusDi != null;
    }

    boolean hasAdx() {
        return adx != null;
    }

    BigDecimal plusDi() {
        return plusDi;
    }

    BigDecimal minusDi() {
        return minusDi;
    }

    BigDecimal adx() {
        return adx;
    }

    void reset() {
        previousHigh = null;
        previousLow = null;
        previousClose = null;
        trSum = BigDecimal.ZERO;
        plusDmSum = BigDecimal.ZERO;
        minusDmSum = BigDecimal.ZERO;
        smoothedTr = null;
        smoothedPlusDm = null;
        smoothedMinusDm = null;
        plusDi = null;
        minusDi = null;
        dxSum = BigDecimal.ZERO;
        dxCount = 0;
        adx = null;
        changes = 0;
    }
}

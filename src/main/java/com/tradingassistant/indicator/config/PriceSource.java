package com.tradingassistant.indicator.config;

import com.tradingassistant.indicator.util.IndicatorMath;
import com.tradingassistant.market.domain.Candle;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Source price used by price-based indicators. The default is the close.
 */
public enum PriceSource {
    OPEN,
    HIGH,
    LOW,
    CLOSE,
    HL2,
    HLC3,
    OHLC4,
    HLCC4;

    public BigDecimal select(Candle candle) {
        Objects.requireNonNull(candle, "candle must not be null");
        return switch (this) {
            case OPEN -> candle.open();
            case HIGH -> candle.high();
            case LOW -> candle.low();
            case CLOSE -> candle.close();
            case HL2 -> IndicatorMath.divide(candle.high().add(candle.low()), BigDecimal.valueOf(2));
            case HLC3 -> IndicatorMath.divide(
                    candle.high().add(candle.low()).add(candle.close()), BigDecimal.valueOf(3));
            case OHLC4 -> IndicatorMath.divide(
                    candle.open().add(candle.high()).add(candle.low()).add(candle.close()),
                    BigDecimal.valueOf(4));
            case HLCC4 -> IndicatorMath.divide(
                    candle.high().add(candle.low()).add(candle.close()).add(candle.close()),
                    BigDecimal.valueOf(4));
        };
    }
}

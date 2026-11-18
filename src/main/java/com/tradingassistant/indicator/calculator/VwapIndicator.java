package com.tradingassistant.indicator.calculator;

import com.tradingassistant.indicator.config.IndicatorConfig;
import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.indicator.domain.IndicatorValues;
import com.tradingassistant.indicator.util.CandleMath;
import com.tradingassistant.indicator.util.IndicatorMath;
import com.tradingassistant.market.domain.Candle;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

public final class VwapIndicator extends AbstractIndicator {

    public static final String TYPE = "VWAP";
    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Kolkata");

    private LocalDate sessionDate;
    private BigDecimal cumulativePriceVolume = BigDecimal.ZERO;
    private BigDecimal cumulativeVolume = BigDecimal.ZERO;

    public VwapIndicator(IndicatorConfig config) {
        super(TYPE, config);
    }

    @Override
    public int minimumCandles() {
        return 1;
    }

    @Override
    protected IndicatorResult compute(Candle candle) {
        LocalDate date = candle.timestamp().atZone(MARKET_ZONE).toLocalDate();
        if (sessionDate == null || !sessionDate.equals(date)) {
            sessionDate = date;
            cumulativePriceVolume = BigDecimal.ZERO;
            cumulativeVolume = BigDecimal.ZERO;
        }

        BigDecimal typical = CandleMath.typicalPrice(candle);
        BigDecimal volume = BigDecimal.valueOf(candle.volume());
        cumulativePriceVolume = cumulativePriceVolume.add(typical.multiply(volume));
        cumulativeVolume = cumulativeVolume.add(volume);

        BigDecimal vwap = cumulativeVolume.signum() == 0
                ? typical
                : IndicatorMath.divide(cumulativePriceVolume, cumulativeVolume);
        return result(candle, Map.of(IndicatorValues.VALUE, vwap));
    }

    @Override
    protected void onReset() {
        sessionDate = null;
        cumulativePriceVolume = BigDecimal.ZERO;
        cumulativeVolume = BigDecimal.ZERO;
    }
}

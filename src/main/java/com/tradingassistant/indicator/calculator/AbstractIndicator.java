package com.tradingassistant.indicator.calculator;

import com.tradingassistant.indicator.api.Indicator;
import com.tradingassistant.indicator.config.IndicatorConfig;
import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.indicator.domain.IndicatorSeries;
import com.tradingassistant.indicator.domain.IndicatorStatus;
import com.tradingassistant.indicator.exception.IndicatorValidationException;
import com.tradingassistant.indicator.validation.CandleSeriesValidator;
import com.tradingassistant.market.domain.Candle;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Shared lifecycle, validation and warm-up handling. Concrete calculators only
 * implement {@link #compute(Candle)} and their mutable state reset.
 */
public abstract class AbstractIndicator implements Indicator {

    private static final String VERSION = "1.0";

    private final String name;
    protected final IndicatorConfig config;
    private Instant lastTimestamp;
    private int processed;

    protected AbstractIndicator(String name, IndicatorConfig config) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    @Override
    public final String name() {
        return name;
    }

    @Override
    public final String version() {
        return VERSION;
    }

    @Override
    public boolean supportsIncremental() {
        return true;
    }

    @Override
    public final IndicatorSeries calculateSeries(List<Candle> candles) {
        CandleSeriesValidator.validate(candles);
        if (candles.size() < minimumCandles()) {
            throw new IndicatorValidationException(
                    name + " requires at least " + minimumCandles()
                            + " candles, received " + candles.size());
        }
        reset();
        List<IndicatorResult> results = new ArrayList<>(candles.size());
        for (Candle candle : candles) {
            results.add(update(candle));
        }
        return new IndicatorSeries(name, version(), results);
    }

    @Override
    public final IndicatorResult update(Candle candle) {
        CandleSeriesValidator.validateNext(candle, lastTimestamp);
        lastTimestamp = candle.timestamp();
        processed++;
        return compute(candle);
    }

    @Override
    public final void reset() {
        lastTimestamp = null;
        processed = 0;
        onReset();
    }

    protected abstract IndicatorResult compute(Candle candle);

    protected abstract void onReset();

    protected final int processed() {
        return processed;
    }

    protected final boolean isReady() {
        return processed >= minimumCandles();
    }

    protected final BigDecimal source(Candle candle) {
        return config.source().select(candle);
    }

    protected final IndicatorResult warming(Candle candle) {
        return new IndicatorResult(
                name,
                version(),
                candle.timestamp(),
                IndicatorStatus.WARMING_UP,
                true,
                Map.of(),
                Map.of("requiredCandles", Integer.toString(minimumCandles())));
    }

    protected final IndicatorResult result(Candle candle, Map<String, BigDecimal> values) {
        return new IndicatorResult(
                name,
                version(),
                candle.timestamp(),
                IndicatorStatus.OK,
                false,
                values,
                Map.of());
    }
}

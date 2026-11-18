package com.tradingassistant.indicator.engine;

import com.tradingassistant.indicator.api.Indicator;
import com.tradingassistant.indicator.api.IndicatorFactory;
import com.tradingassistant.indicator.calculator.AdxIndicator;
import com.tradingassistant.indicator.calculator.AtrIndicator;
import com.tradingassistant.indicator.calculator.BollingerBandsIndicator;
import com.tradingassistant.indicator.calculator.CmfIndicator;
import com.tradingassistant.indicator.calculator.DmiIndicator;
import com.tradingassistant.indicator.calculator.DonchianChannelsIndicator;
import com.tradingassistant.indicator.calculator.EmaIndicator;
import com.tradingassistant.indicator.calculator.KeltnerChannelsIndicator;
import com.tradingassistant.indicator.calculator.MacdIndicator;
import com.tradingassistant.indicator.calculator.ObvIndicator;
import com.tradingassistant.indicator.calculator.RsiIndicator;
import com.tradingassistant.indicator.calculator.SmaIndicator;
import com.tradingassistant.indicator.calculator.StochasticRsiIndicator;
import com.tradingassistant.indicator.calculator.SupertrendIndicator;
import com.tradingassistant.indicator.calculator.VwapIndicator;
import com.tradingassistant.indicator.calculator.WmaIndicator;
import com.tradingassistant.indicator.config.IndicatorConfig;
import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.indicator.domain.IndicatorSeries;
import com.tradingassistant.indicator.exception.IndicatorValidationException;
import com.tradingassistant.indicator.validation.CandleGap;
import com.tradingassistant.indicator.validation.CandleSeriesValidator;
import com.tradingassistant.market.domain.Candle;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Registry and entry point for indicator creation and historical calculation.
 * Incremental state belongs to the created {@link Indicator} instance, so one
 * engine can safely serve independent candle streams.
 */
public final class IndicatorEngine {

    private final Map<String, IndicatorFactory> factories;

    public IndicatorEngine(List<IndicatorFactory> factories) {
        Objects.requireNonNull(factories, "factories must not be null");
        Map<String, IndicatorFactory> registered = new LinkedHashMap<>();
        for (IndicatorFactory factory : factories) {
            Objects.requireNonNull(factory, "factory must not be null");
            String key = normalize(factory.type());
            if (registered.putIfAbsent(key, factory) != null) {
                throw new IndicatorValidationException("Duplicate indicator type: " + factory.type());
            }
        }
        this.factories = Map.copyOf(registered);
    }

    public static IndicatorEngine standard() {
        return new IndicatorEngine(List.of(
                factory(SmaIndicator.TYPE, SmaIndicator::new),
                factory(EmaIndicator.TYPE, EmaIndicator::new),
                factory(WmaIndicator.TYPE, WmaIndicator::new),
                factory(RsiIndicator.TYPE, RsiIndicator::new),
                factory(MacdIndicator.TYPE, MacdIndicator::new),
                factory(StochasticRsiIndicator.TYPE, StochasticRsiIndicator::new),
                factory(AtrIndicator.TYPE, AtrIndicator::new),
                factory(BollingerBandsIndicator.TYPE, BollingerBandsIndicator::new),
                factory(KeltnerChannelsIndicator.TYPE, KeltnerChannelsIndicator::new),
                factory(DonchianChannelsIndicator.TYPE, DonchianChannelsIndicator::new),
                factory(AdxIndicator.TYPE, AdxIndicator::new),
                factory(DmiIndicator.TYPE, DmiIndicator::new),
                factory(VwapIndicator.TYPE, VwapIndicator::new),
                factory(ObvIndicator.TYPE, ObvIndicator::new),
                factory(CmfIndicator.TYPE, CmfIndicator::new),
                factory(SupertrendIndicator.TYPE, SupertrendIndicator::new)));
    }

    public Indicator create(String type, IndicatorConfig config) {
        return factory(type).create(Objects.requireNonNull(config, "config must not be null"));
    }

    public Indicator create(String type) {
        return create(type, IndicatorConfig.defaults());
    }

    public IndicatorSeries calculateSeries(String type, IndicatorConfig config, List<Candle> candles) {
        return create(type, config).calculateSeries(candles);
    }

    public IndicatorResult calculateLatest(String type, IndicatorConfig config, List<Candle> candles) {
        return create(type, config).calculate(candles);
    }

    public Set<String> supportedIndicators() {
        return factories.keySet();
    }

    /**
     * Detects interval gaps without filling or rejecting them.
     */
    public List<CandleGap> detectGaps(List<Candle> candles) {
        return CandleSeriesValidator.findGaps(candles);
    }

    public boolean supports(String type) {
        return type != null && !type.isBlank()
                && factories.containsKey(normalize(type));
    }

    private IndicatorFactory factory(String type) {
        if (type == null || type.isBlank()) {
            throw new IndicatorValidationException("Indicator type must not be blank.");
        }
        IndicatorFactory factory = factories.get(normalize(type));
        if (factory == null) {
            throw new IndicatorValidationException("Unsupported indicator: " + type);
        }
        return factory;
    }

    private static String normalize(String type) {
        return type.toUpperCase(Locale.ROOT);
    }

    private static IndicatorFactory factory(
            String type,
            java.util.function.Function<IndicatorConfig, Indicator> creator) {
        return new NamedIndicatorFactory(type, creator);
    }
}

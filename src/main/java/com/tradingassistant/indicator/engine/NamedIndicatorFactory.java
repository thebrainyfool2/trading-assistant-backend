package com.tradingassistant.indicator.engine;

import com.tradingassistant.indicator.api.Indicator;
import com.tradingassistant.indicator.api.IndicatorFactory;
import com.tradingassistant.indicator.config.IndicatorConfig;

import java.util.Objects;
import java.util.function.Function;

public record NamedIndicatorFactory(
        String type,
        Function<IndicatorConfig, Indicator> creator
) implements IndicatorFactory {

    public NamedIndicatorFactory {
        type = Objects.requireNonNull(type, "type must not be null");
        creator = Objects.requireNonNull(creator, "creator must not be null");
    }

    @Override
    public Indicator create(IndicatorConfig config) {
        return creator.apply(config);
    }
}

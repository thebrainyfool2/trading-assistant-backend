package com.tradingassistant.indicator.api;

import com.tradingassistant.indicator.config.IndicatorConfig;

/**
 * Extension point for registering indicator implementations without changing
 * the engine.
 */
public interface IndicatorFactory {

    String type();

    Indicator create(IndicatorConfig config);
}

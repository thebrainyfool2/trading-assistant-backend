package com.tradingassistant.execution.service;

import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.Interval;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Supplies the latest price used to mark open positions.
 *
 * <p>The contract is deliberately tiny: execution needs a mark, not an indicator,
 * a candle history or an opinion about the market.</p>
 */
@FunctionalInterface
public interface ExecutionMarketDataProvider {

    Optional<BigDecimal> latestPrice(InstrumentKey instrument, Interval interval);

    /**
     * Provider that supplies no marks.
     */
    static ExecutionMarketDataProvider empty() {
        return (instrument, interval) -> Optional.empty();
    }
}

package com.tradingassistant.intelligence.api;

import com.tradingassistant.intelligence.config.MarketIntelligenceConfig;
import com.tradingassistant.intelligence.domain.AnalysisContext;

/**
 * One independent aspect of market behaviour.
 *
 * <p>An analyzer reads only the validated {@link AnalysisContext} and the
 * configuration, and returns one immutable assessment. It must not call another
 * analyzer and must not keep state between calls: composition belongs to the
 * engine, so an aspect can be replaced, tested or reordered without touching the
 * others.</p>
 *
 * @param <R> immutable assessment type produced by this analyzer
 */
public interface MarketAnalyzer<R> {

    /**
     * Stable analyzer name used in diagnostics and tests.
     */
    String name();

    /**
     * Describes one aspect. The context is guaranteed to be coherent; the
     * analyzer must not read outside it.
     */
    R analyze(AnalysisContext context, MarketIntelligenceConfig config);
}

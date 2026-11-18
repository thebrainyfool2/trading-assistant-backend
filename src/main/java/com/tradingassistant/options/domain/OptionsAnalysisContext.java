package com.tradingassistant.options.domain;

import com.tradingassistant.intelligence.domain.MarketAssessment;
import com.tradingassistant.options.exception.OptionsValidationException;

import java.util.Objects;

/**
 * The single validated input boundary of the option analytics engine.
 *
 * <p>The context ties a chain capture to the market assessment of the same
 * instant. Diagnostics are derived from that pair instead of from pipeline
 * internals, so an assessment can be reproduced from the two inputs alone.</p>
 *
 * @param chain           captured option chain
 * @param marketAssessment market assessment of the underlying at the same instant
 * @param previousChain   previous leg state, empty when no earlier capture exists
 */
public record OptionsAnalysisContext(
        ChainSnapshot chain,
        MarketAssessment marketAssessment,
        ChainStateSnapshot previousChain) {

    public OptionsAnalysisContext {
        chain = Objects.requireNonNull(chain, "chain must not be null");
        marketAssessment = Objects.requireNonNull(marketAssessment,
                "marketAssessment must not be null");
        previousChain = previousChain == null ? ChainStateSnapshot.empty() : previousChain;
        if (!chain.spotInstrument().equals(marketAssessment.instrument())) {
            throw new OptionsValidationException(
                    "chain and market assessment must describe the same instrument");
        }
        if (!chain.capturedAt().equals(marketAssessment.timestamp())) {
            throw new OptionsValidationException(
                    "chain capture timestamp must match the market assessment timestamp");
        }
    }

    public static OptionsAnalysisContext of(ChainSnapshot chain, MarketAssessment assessment) {
        return new OptionsAnalysisContext(chain, assessment, ChainStateSnapshot.empty());
    }
}

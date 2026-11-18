package com.tradingassistant.intelligence.analysis;

import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.indicator.domain.IndicatorValues;
import com.tradingassistant.intelligence.api.MarketAnalyzer;
import com.tradingassistant.intelligence.config.MarketIntelligenceConfig;
import com.tradingassistant.intelligence.domain.AnalysisContext;
import com.tradingassistant.intelligence.domain.AssessmentStatus;
import com.tradingassistant.intelligence.domain.VolatilityAssessment;
import com.tradingassistant.intelligence.domain.VolatilityState;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Describes whether the trading range is widening, narrowing or unchanged.
 *
 * <p>Expansion is always a comparison, never an absolute level: each input
 * compares its current width with its most recent earlier width. ATR uses its
 * value; Bollinger, Keltner and Donchian use the distance between their upper
 * and lower bands. A relative change smaller than
 * {@code volatilityChangeThreshold} is reported as stable, which keeps ordinary
 * noise from being labelled as expansion.</p>
 */
public final class VolatilityAnalyzer implements MarketAnalyzer<VolatilityAssessment> {

    public static final String NAME = "volatility";

    private static final int EXPECTED_INPUTS = 4;
    private static final String UNKNOWN_SUMMARY =
            "Volatility cannot be determined from the available inputs.";
    private static final String NOT_READY_SUMMARY =
            "Volatility is not described until every volatility input is ready.";
    private static final String EXPANDING_SUMMARY =
            "Range width is widening across the volatility inputs.";
    private static final String CONTRACTING_SUMMARY =
            "Range width is narrowing across the volatility inputs.";
    private static final String STABLE_SUMMARY =
            "Range width is unchanged within the configured tolerance.";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public VolatilityAssessment analyze(AnalysisContext context, MarketIntelligenceConfig config) {
        Map<String, IndicatorResult> latest = context.latestResults();
        Set<String> used = new LinkedHashSet<>();

        List<Change> changes = List.of(
                new Change("ATR",
                        IndicatorInputs.readyValue(latest, "ATR", IndicatorValues.VALUE),
                        IndicatorInputs.previousValue(context, "ATR", IndicatorValues.VALUE)),
                new Change("BOLLINGER",
                        IndicatorInputs.readyBandWidth(latest, "BOLLINGER"),
                        IndicatorInputs.previousBandWidth(context, "BOLLINGER")),
                new Change("KELTNER",
                        IndicatorInputs.readyBandWidth(latest, "KELTNER"),
                        IndicatorInputs.previousBandWidth(context, "KELTNER")),
                new Change("DONCHIAN",
                        IndicatorInputs.readyBandWidth(latest, "DONCHIAN"),
                        IndicatorInputs.previousBandWidth(context, "DONCHIAN")));

        int expanding = 0;
        int contracting = 0;
        for (Change change : changes) {
            if (change.current().isEmpty() || change.previous().isEmpty()) {
                continue;
            }
            Optional<BigDecimal> relative = IndicatorInputs.relativeChange(
                    change.current().get(), change.previous().get());
            if (relative.isEmpty()) {
                continue;
            }
            used.add(change.type());
            BigDecimal movement = relative.get();
            if (movement.compareTo(config.volatilityChangeThreshold()) > 0) {
                expanding++;
            } else if (movement.compareTo(config.volatilityChangeThreshold().negate()) < 0) {
                contracting++;
            }
        }

        AssessmentStatus status = IndicatorInputs.status(used.size(), EXPECTED_INPUTS);
        if (used.isEmpty()) {
            return new VolatilityAssessment(VolatilityState.UNKNOWN, status, used, UNKNOWN_SUMMARY);
        }
        if (config.warmupPolicy().blocksAssessment(status)) {
            return new VolatilityAssessment(VolatilityState.UNKNOWN, status, used, NOT_READY_SUMMARY);
        }

        int net = expanding - contracting;
        VolatilityState state;
        if (net > 0) {
            state = VolatilityState.EXPANDING;
        } else if (net < 0) {
            state = VolatilityState.CONTRACTING;
        } else {
            state = VolatilityState.STABLE;
        }
        return new VolatilityAssessment(state, status, used, summaryFor(state));
    }

    private static String summaryFor(VolatilityState state) {
        return switch (state) {
            case EXPANDING -> EXPANDING_SUMMARY;
            case CONTRACTING -> CONTRACTING_SUMMARY;
            case STABLE -> STABLE_SUMMARY;
            case UNKNOWN -> UNKNOWN_SUMMARY;
        };
    }

    /**
     * Current and previous width of one volatility input.
     */
    private record Change(String type, Optional<BigDecimal> current, Optional<BigDecimal> previous) {
    }
}

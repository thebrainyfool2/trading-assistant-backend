package com.tradingassistant.intelligence.analysis;

import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.indicator.domain.IndicatorValues;
import com.tradingassistant.intelligence.api.MarketAnalyzer;
import com.tradingassistant.intelligence.config.MarketIntelligenceConfig;
import com.tradingassistant.intelligence.domain.AnalysisContext;
import com.tradingassistant.intelligence.domain.AssessmentStatus;
import com.tradingassistant.intelligence.domain.ParticipationAssessment;
import com.tradingassistant.intelligence.domain.ParticipationState;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Describes how much volume is taking part in the move.
 *
 * <p>On-balance volume contributes the direction of its change, the Chaikin
 * money flow contributes its signed level against a configurable threshold, and
 * price relative to session VWAP contributes whether recent trading is above or
 * below the session's volume-weighted price. The result says whether
 * participation is strong, average or weak; it says nothing about what to do.</p>
 */
public final class ParticipationAnalyzer implements MarketAnalyzer<ParticipationAssessment> {

    public static final String NAME = "participation";

    private static final int EXPECTED_INPUTS = 3;
    private static final String UNKNOWN_SUMMARY =
            "Participation cannot be determined from the available inputs.";
    private static final String NOT_READY_SUMMARY =
            "Participation is not described until every participation input is ready.";
    private static final String STRONG_SUMMARY =
            "Volume-based inputs point to strong participation.";
    private static final String WEAK_SUMMARY =
            "Volume-based inputs point to weak participation.";
    private static final String AVERAGE_SUMMARY =
            "Volume-based inputs are balanced or average.";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ParticipationAssessment analyze(AnalysisContext context, MarketIntelligenceConfig config) {
        Map<String, IndicatorResult> latest = context.latestResults();
        Set<String> used = new LinkedHashSet<>();
        int strong = 0;
        int weak = 0;

        Optional<BigDecimal> obv = IndicatorInputs.readyValue(latest, "OBV", IndicatorValues.VALUE);
        Optional<BigDecimal> previousObv = IndicatorInputs.previousValue(context, "OBV", IndicatorValues.VALUE);
        if (obv.isPresent() && previousObv.isPresent()) {
            used.add("OBV");
            int comparison = obv.get().compareTo(previousObv.get());
            if (comparison > 0) {
                strong++;
            } else if (comparison < 0) {
                weak++;
            }
        }

        Optional<BigDecimal> cmf = IndicatorInputs.readyValue(latest, "CMF", IndicatorValues.VALUE);
        if (cmf.isPresent()) {
            used.add("CMF");
            if (cmf.get().compareTo(config.participationCmfThreshold()) >= 0) {
                strong++;
            } else if (cmf.get().compareTo(config.participationCmfThreshold().negate()) <= 0) {
                weak++;
            }
        }

        Optional<BigDecimal> vwap = IndicatorInputs.readyValue(latest, "VWAP", IndicatorValues.VALUE);
        if (vwap.isPresent()) {
            used.add("VWAP");
            int comparison = context.latestCandle().close().compareTo(vwap.get());
            if (comparison > 0) {
                strong++;
            } else if (comparison < 0) {
                weak++;
            }
        }

        AssessmentStatus status = IndicatorInputs.status(used.size(), EXPECTED_INPUTS);
        if (used.isEmpty()) {
            return new ParticipationAssessment(ParticipationState.UNKNOWN, status, used, UNKNOWN_SUMMARY);
        }
        if (config.warmupPolicy().blocksAssessment(status)) {
            return new ParticipationAssessment(
                    ParticipationState.UNKNOWN, status, used, NOT_READY_SUMMARY);
        }

        int net = strong - weak;
        ParticipationState state;
        if (net > 0) {
            state = ParticipationState.STRONG;
        } else if (net < 0) {
            state = ParticipationState.WEAK;
        } else {
            state = ParticipationState.AVERAGE;
        }
        return new ParticipationAssessment(state, status, used, summaryFor(state));
    }

    private static String summaryFor(ParticipationState state) {
        return switch (state) {
            case STRONG -> STRONG_SUMMARY;
            case WEAK -> WEAK_SUMMARY;
            case AVERAGE -> AVERAGE_SUMMARY;
            case UNKNOWN -> UNKNOWN_SUMMARY;
        };
    }
}

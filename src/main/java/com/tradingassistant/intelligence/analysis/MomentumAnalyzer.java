package com.tradingassistant.intelligence.analysis;

import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.indicator.domain.IndicatorValues;
import com.tradingassistant.intelligence.api.MarketAnalyzer;
import com.tradingassistant.intelligence.config.MarketIntelligenceConfig;
import com.tradingassistant.intelligence.domain.AnalysisContext;
import com.tradingassistant.intelligence.domain.AssessmentStatus;
import com.tradingassistant.intelligence.domain.MomentumAssessment;
import com.tradingassistant.intelligence.domain.MomentumState;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Describes whether momentum is gaining or fading using RSI, the MACD histogram
 * and Stochastic RSI.
 *
 * <p>RSI uses the configured upper and lower bands, so the neutral zone is a
 * configuration choice rather than a hardcoded 70/30 or 50 level. MACD compares
 * the histogram with zero and Stochastic RSI compares %K with %D. Each usable
 * input votes; a tie is reported as neutral, which is a description, not a
 * signal.</p>
 */
public final class MomentumAnalyzer implements MarketAnalyzer<MomentumAssessment> {

    public static final String NAME = "momentum";

    private static final int EXPECTED_INPUTS = 3;
    private static final String UNKNOWN_SUMMARY =
            "Momentum cannot be determined from the available inputs.";
    private static final String NOT_READY_SUMMARY =
            "Momentum is not described until every momentum input is ready.";
    private static final String STRENGTHENING_SUMMARY =
            "Momentum inputs point to gaining momentum.";
    private static final String WEAKENING_SUMMARY =
            "Momentum inputs point to fading momentum.";
    private static final String NEUTRAL_SUMMARY =
            "Momentum inputs are balanced or neutral.";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public MomentumAssessment analyze(AnalysisContext context, MarketIntelligenceConfig config) {
        Map<String, IndicatorResult> latest = context.latestResults();
        Set<String> used = new LinkedHashSet<>();
        int strengthening = 0;
        int weakening = 0;

        Optional<BigDecimal> rsi = IndicatorInputs.readyValue(latest, "RSI", IndicatorValues.VALUE);
        if (rsi.isPresent()) {
            used.add("RSI");
            if (rsi.get().compareTo(config.momentumUpperThreshold()) >= 0) {
                strengthening++;
            } else if (rsi.get().compareTo(config.momentumLowerThreshold()) <= 0) {
                weakening++;
            }
        }

        Optional<BigDecimal> histogram = IndicatorInputs.readyValue(
                latest, "MACD", IndicatorValues.HISTOGRAM);
        if (histogram.isPresent()) {
            used.add("MACD");
            int signum = histogram.get().signum();
            if (signum > 0) {
                strengthening++;
            } else if (signum < 0) {
                weakening++;
            }
        }

        Optional<BigDecimal> percentK = IndicatorInputs.readyValue(
                latest, "STOCH_RSI", IndicatorValues.PERCENT_K);
        Optional<BigDecimal> percentD = IndicatorInputs.readyValue(
                latest, "STOCH_RSI", IndicatorValues.PERCENT_D);
        if (percentK.isPresent() && percentD.isPresent()) {
            used.add("STOCH_RSI");
            int comparison = percentK.get().compareTo(percentD.get());
            if (comparison > 0) {
                strengthening++;
            } else if (comparison < 0) {
                weakening++;
            }
        }

        AssessmentStatus status = IndicatorInputs.status(used.size(), EXPECTED_INPUTS);
        if (used.isEmpty()) {
            return new MomentumAssessment(MomentumState.UNKNOWN, status, used, UNKNOWN_SUMMARY);
        }
        if (config.warmupPolicy().blocksAssessment(status)) {
            return new MomentumAssessment(MomentumState.UNKNOWN, status, used, NOT_READY_SUMMARY);
        }

        int net = strengthening - weakening;
        MomentumState state;
        if (net > 0) {
            state = MomentumState.STRENGTHENING;
        } else if (net < 0) {
            state = MomentumState.WEAKENING;
        } else {
            state = MomentumState.NEUTRAL;
        }
        return new MomentumAssessment(state, status, used, summaryFor(state));
    }

    private static String summaryFor(MomentumState state) {
        return switch (state) {
            case STRENGTHENING -> STRENGTHENING_SUMMARY;
            case WEAKENING -> WEAKENING_SUMMARY;
            case NEUTRAL -> NEUTRAL_SUMMARY;
            case UNKNOWN -> UNKNOWN_SUMMARY;
        };
    }
}

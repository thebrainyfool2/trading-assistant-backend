package com.tradingassistant.intelligence.analysis;

import com.tradingassistant.intelligence.api.MarketAnalyzer;
import com.tradingassistant.intelligence.config.MarketIntelligenceConfig;
import com.tradingassistant.intelligence.domain.AnalysisContext;
import com.tradingassistant.intelligence.domain.AssessmentStatus;
import com.tradingassistant.intelligence.domain.StructureAssessment;
import com.tradingassistant.intelligence.domain.StructureBias;
import com.tradingassistant.intelligence.domain.StructureSignal;
import com.tradingassistant.market.domain.Candle;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Describes swing structure from candles alone, without reading any indicator.
 *
 * <p>The window is split into an older and a newer half. The highest high and
 * lowest low of each half are compared, so "higher high" and "lower low" are
 * statements about the window rather than about a subjective pivot label. When
 * both comparisons stay inside the configured tolerance the structure is a
 * range. A close beyond every earlier high or low in the window is reported as a
 * breakout or breakdown <em>candidate</em>: a geometric observation, never a
 * recommendation.</p>
 *
 * <p>At least four candles are required for a split comparison. A window shorter
 * than the configured lookback is described as {@code PARTIAL}.</p>
 */
public final class StructureAnalyzer implements MarketAnalyzer<StructureAssessment> {

    public static final String NAME = "structure";

    private static final int MINIMUM_CANDLES = 4;
    private static final MathContext DIVISION = MathContext.DECIMAL128;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private static final String INSUFFICIENT_SUMMARY =
            "At least four candles are required to describe structure.";
    private static final String NOT_READY_SUMMARY =
            "Structure is not described until the full lookback window is available.";
    private static final String RANGE_SUMMARY =
            "Highs and lows stayed within the configured tolerance.";
    private static final String UPTREND_SUMMARY =
            "Structure printed higher highs and higher lows.";
    private static final String DOWNTREND_SUMMARY =
            "Structure printed lower highs and lower lows.";
    private static final String MIXED_SUMMARY =
            "Structure is mixed within the lookback window.";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public StructureAssessment analyze(AnalysisContext context, MarketIntelligenceConfig config) {
        int available = context.candleCount();
        int lookback = Math.min(config.structureLookback(), available);
        if (lookback < MINIMUM_CANDLES) {
            return new StructureAssessment(
                    StructureBias.UNKNOWN, Set.of(), AssessmentStatus.INSUFFICIENT_DATA,
                    INSUFFICIENT_SUMMARY);
        }

        List<Candle> window = context.candles().subList(available - lookback, available);
        int middle = lookback / 2;

        BigDecimal olderHigh = highestHigh(window, 0, middle);
        BigDecimal newerHigh = highestHigh(window, middle, lookback);
        BigDecimal olderLow = lowestLow(window, 0, middle);
        BigDecimal newerLow = lowestLow(window, middle, lookback);

        Set<StructureSignal> signals = new LinkedHashSet<>();
        BigDecimal tolerance = config.structureRangeTolerancePercent();
        boolean highInRange = driftPercent(olderHigh, newerHigh).compareTo(tolerance) <= 0;
        boolean lowInRange = driftPercent(olderLow, newerLow).compareTo(tolerance) <= 0;

        if (highInRange && lowInRange) {
            signals.add(StructureSignal.RANGE);
        } else {
            addSignal(signals, newerHigh.compareTo(olderHigh),
                    StructureSignal.HIGHER_HIGH, StructureSignal.LOWER_HIGH);
            addSignal(signals, newerLow.compareTo(olderLow),
                    StructureSignal.HIGHER_LOW, StructureSignal.LOWER_LOW);
        }

        BigDecimal priorHigh = highestHigh(window, 0, lookback - 1);
        BigDecimal priorLow = lowestLow(window, 0, lookback - 1);
        BigDecimal latestClose = window.getLast().close();
        if (latestClose.compareTo(priorHigh) > 0) {
            signals.add(StructureSignal.BREAKOUT_CANDIDATE);
        }
        if (latestClose.compareTo(priorLow) < 0) {
            signals.add(StructureSignal.BREAKDOWN_CANDIDATE);
        }

        StructureBias bias;
        if (signals.contains(StructureSignal.RANGE)) {
            bias = StructureBias.RANGING;
        } else if (signals.contains(StructureSignal.HIGHER_HIGH)
                && signals.contains(StructureSignal.HIGHER_LOW)) {
            bias = StructureBias.UPTREND;
        } else if (signals.contains(StructureSignal.LOWER_HIGH)
                && signals.contains(StructureSignal.LOWER_LOW)) {
            bias = StructureBias.DOWNTREND;
        } else {
            bias = StructureBias.UNKNOWN;
        }

        AssessmentStatus status = lookback >= config.structureLookback()
                ? AssessmentStatus.READY
                : AssessmentStatus.PARTIAL;
        if (config.warmupPolicy().blocksAssessment(status)) {
            return new StructureAssessment(
                    StructureBias.UNKNOWN, signals, status, NOT_READY_SUMMARY);
        }
        return new StructureAssessment(bias, signals, status, summaryFor(bias));
    }

    private static void addSignal(
            Set<StructureSignal> signals,
            int comparison,
            StructureSignal higher,
            StructureSignal lower) {
        if (comparison > 0) {
            signals.add(higher);
        } else if (comparison < 0) {
            signals.add(lower);
        }
    }

    private static BigDecimal highestHigh(List<Candle> candles, int from, int to) {
        BigDecimal highest = candles.get(from).high();
        for (int index = from + 1; index < to; index++) {
            if (candles.get(index).high().compareTo(highest) > 0) {
                highest = candles.get(index).high();
            }
        }
        return highest;
    }

    private static BigDecimal lowestLow(List<Candle> candles, int from, int to) {
        BigDecimal lowest = candles.get(from).low();
        for (int index = from + 1; index < to; index++) {
            if (candles.get(index).low().compareTo(lowest) < 0) {
                lowest = candles.get(index).low();
            }
        }
        return lowest;
    }

    /**
     * Absolute percentage drift between an older and a newer extreme. A zero
     * baseline cannot be compared and is reported as a full drift, so it never
     * masquerades as a range.
     */
    private static BigDecimal driftPercent(BigDecimal older, BigDecimal newer) {
        if (older.signum() == 0) {
            return ONE_HUNDRED;
        }
        return newer.subtract(older).abs().divide(older, DIVISION).multiply(ONE_HUNDRED);
    }

    private static String summaryFor(StructureBias bias) {
        return switch (bias) {
            case RANGING -> RANGE_SUMMARY;
            case UPTREND -> UPTREND_SUMMARY;
            case DOWNTREND -> DOWNTREND_SUMMARY;
            case UNKNOWN -> MIXED_SUMMARY;
        };
    }
}

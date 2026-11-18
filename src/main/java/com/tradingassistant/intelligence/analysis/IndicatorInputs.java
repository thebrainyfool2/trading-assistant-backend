package com.tradingassistant.intelligence.analysis;

import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.indicator.domain.IndicatorSeries;
import com.tradingassistant.indicator.domain.IndicatorValues;
import com.tradingassistant.intelligence.domain.AnalysisContext;
import com.tradingassistant.intelligence.domain.AssessmentStatus;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Map;
import java.util.Optional;

/**
 * Lookup rules shared by the analyzers.
 *
 * <p>An observation is usable only when it is present, not warming up, and
 * carries the requested named value. The previous observation is the most recent
 * earlier observation that satisfies the same rule, so a warm-up row in between
 * neither hides history nor becomes a fake baseline.</p>
 */
final class IndicatorInputs {

    private static final MathContext DIVISION = MathContext.DECIMAL128;

    private IndicatorInputs() {
    }

    static Optional<BigDecimal> readyValue(
            Map<String, IndicatorResult> latest,
            String indicatorType,
            String valueKey) {
        IndicatorResult result = latest.get(indicatorType);
        if (!usable(result)) {
            return Optional.empty();
        }
        return Optional.ofNullable(result.values().get(valueKey));
    }

    static Optional<BigDecimal> previousValue(
            AnalysisContext context,
            String indicatorType,
            String valueKey) {
        Optional<IndicatorSeries> series = context.series(indicatorType);
        if (series.isEmpty()) {
            return Optional.empty();
        }
        return previous(series.get(), result -> usable(result) && result.values().get(valueKey) != null)
                .map(result -> result.values().get(valueKey));
    }

    /**
     * Width between the upper and lower named values of the latest observation.
     */
    static Optional<BigDecimal> readyBandWidth(
            Map<String, IndicatorResult> latest,
            String indicatorType) {
        return bandWidth(latest.get(indicatorType));
    }

    /**
     * Width between the upper and lower named values of the most recent earlier
     * observation.
     */
    static Optional<BigDecimal> previousBandWidth(
            AnalysisContext context,
            String indicatorType) {
        return context.series(indicatorType)
                .flatMap(series -> previous(series, IndicatorInputs::hasBand))
                .flatMap(IndicatorInputs::bandWidth);
    }

    static Optional<BigDecimal> relativeChange(BigDecimal current, BigDecimal previous) {
        if (current == null || previous == null || previous.signum() == 0) {
            return Optional.empty();
        }
        return Optional.of(current.subtract(previous).divide(previous, DIVISION));
    }

    static AssessmentStatus status(int usableInputs, int expectedInputs) {
        if (usableInputs <= 0) {
            return AssessmentStatus.INSUFFICIENT_DATA;
        }
        return usableInputs >= expectedInputs ? AssessmentStatus.READY : AssessmentStatus.PARTIAL;
    }

    private static boolean usable(IndicatorResult result) {
        return result != null && !result.warmup() && result.hasValues();
    }

    private static boolean hasBand(IndicatorResult result) {
        return usable(result)
                && result.values().get(IndicatorValues.UPPER) != null
                && result.values().get(IndicatorValues.LOWER) != null;
    }

    private static Optional<BigDecimal> bandWidth(IndicatorResult result) {
        if (!hasBand(result)) {
            return Optional.empty();
        }
        return Optional.of(result.values().get(IndicatorValues.UPPER)
                .subtract(result.values().get(IndicatorValues.LOWER)));
    }

    private static Optional<IndicatorResult> previous(
            IndicatorSeries series,
            java.util.function.Predicate<IndicatorResult> condition) {
        var results = series.results();
        for (int index = results.size() - 2; index >= 0; index--) {
            IndicatorResult candidate = results.get(index);
            if (condition.test(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }
}

package com.tradingassistant.intelligence.analysis;

import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.indicator.domain.IndicatorValues;
import com.tradingassistant.intelligence.api.MarketAnalyzer;
import com.tradingassistant.intelligence.config.MarketIntelligenceConfig;
import com.tradingassistant.intelligence.domain.AnalysisContext;
import com.tradingassistant.intelligence.domain.AssessmentStatus;
import com.tradingassistant.intelligence.domain.TrendAssessment;
import com.tradingassistant.intelligence.domain.TrendState;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Describes the trend from whatever configured trend inputs are available.
 *
 * <p>Three price-based inputs vote: the close relative to EMA, the close
 * relative to SMA, and the Supertrend direction. ADX is used only as a strength
 * gate, never as a direction. The rules are configuration, not constants:</p>
 * <ul>
 *   <li>{@code trendAgreementVotes} directional votes are needed for a
 *       direction;</li>
 *   <li>{@code trendAdxThreshold} decides whether ADX confirms strength;</li>
 *   <li>{@code requireAdxForDirection} refuses a direction while ADX is
 *       unavailable or weak.</li>
 * </ul>
 * No single indicator can decide the trend on its own unless the configuration
 * is changed to require a single vote.
 */
public final class TrendAnalyzer implements MarketAnalyzer<TrendAssessment> {

    public static final String NAME = "trend";

    private static final int PRICE_INPUTS = 3;
    private static final String UNKNOWN_SUMMARY =
            "Trend cannot be determined from the available inputs.";
    private static final String NOT_READY_SUMMARY =
            "Trend is not described until every trend input is ready.";
    private static final String ADX_REQUIRED_SUMMARY =
            "ADX is required by configuration and is not available.";
    private static final String BULLISH_SUMMARY =
            "Trend inputs agree on an upward direction.";
    private static final String BEARISH_SUMMARY =
            "Trend inputs agree on a downward direction.";
    private static final String SIDEWAYS_SUMMARY =
            "Trend inputs conflict or trend strength is insufficient.";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public TrendAssessment analyze(AnalysisContext context, MarketIntelligenceConfig config) {
        Map<String, IndicatorResult> latest = context.latestResults();
        BigDecimal close = context.latestCandle().close();
        Set<String> used = new LinkedHashSet<>();
        int bullish = 0;
        int bearish = 0;
        int directionalInputs = 0;

        Optional<BigDecimal> ema = IndicatorInputs.readyValue(latest, "EMA", IndicatorValues.VALUE);
        if (ema.isPresent()) {
            used.add("EMA");
            directionalInputs++;
            int comparison = close.compareTo(ema.get());
            if (comparison > 0) {
                bullish++;
            } else if (comparison < 0) {
                bearish++;
            }
        }

        Optional<BigDecimal> sma = IndicatorInputs.readyValue(latest, "SMA", IndicatorValues.VALUE);
        if (sma.isPresent()) {
            used.add("SMA");
            directionalInputs++;
            int comparison = close.compareTo(sma.get());
            if (comparison > 0) {
                bullish++;
            } else if (comparison < 0) {
                bearish++;
            }
        }

        Optional<BigDecimal> direction = IndicatorInputs.readyValue(
                latest, "SUPERTREND", IndicatorValues.DIRECTION);
        if (direction.isPresent()) {
            used.add("SUPERTREND");
            directionalInputs++;
            int signum = direction.get().signum();
            if (signum > 0) {
                bullish++;
            } else if (signum < 0) {
                bearish++;
            }
        }

        Optional<BigDecimal> adx = IndicatorInputs.readyValue(latest, "ADX", IndicatorValues.ADX);
        boolean adxRequired = config.requireAdxForDirection();
        boolean adxAvailable = adx.isPresent() && adxRequired;
        if (adxAvailable) {
            used.add("ADX");
        }
        int expected = adxRequired ? PRICE_INPUTS + 1 : PRICE_INPUTS;
        AssessmentStatus status = IndicatorInputs.status(used.size(), expected);
        if (directionalInputs == 0) {
            return new TrendAssessment(TrendState.UNKNOWN, status, used, UNKNOWN_SUMMARY);
        }
        if (adxRequired && adx.isEmpty()) {
            return new TrendAssessment(TrendState.UNKNOWN, status, used, ADX_REQUIRED_SUMMARY);
        }

        boolean strengthConfirmed = !adxRequired
                || adx.get().compareTo(config.trendAdxThreshold()) >= 0;

        int net = bullish - bearish;
        TrendState state;
        if (!strengthConfirmed) {
            state = TrendState.SIDEWAYS;
        } else if (net >= config.trendAgreementVotes()) {
            state = TrendState.BULLISH;
        } else if (-net >= config.trendAgreementVotes()) {
            state = TrendState.BEARISH;
        } else {
            state = TrendState.SIDEWAYS;
        }

        if (config.warmupPolicy().blocksAssessment(status)) {
            return new TrendAssessment(TrendState.UNKNOWN, status, used, NOT_READY_SUMMARY);
        }
        return new TrendAssessment(state, status, used, summaryFor(state));
    }

    private static String summaryFor(TrendState state) {
        return switch (state) {
            case BULLISH -> BULLISH_SUMMARY;
            case BEARISH -> BEARISH_SUMMARY;
            case SIDEWAYS -> SIDEWAYS_SUMMARY;
            case UNKNOWN -> UNKNOWN_SUMMARY;
        };
    }
}

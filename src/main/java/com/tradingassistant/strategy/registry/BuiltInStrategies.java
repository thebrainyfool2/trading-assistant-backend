package com.tradingassistant.strategy.registry;

import com.tradingassistant.intelligence.domain.StructureSignal;
import com.tradingassistant.intelligence.domain.VolatilityState;
import com.tradingassistant.strategy.domain.StrategyCategory;
import com.tradingassistant.strategy.domain.StrategyDefinition;
import com.tradingassistant.strategy.domain.StrategyInput;
import com.tradingassistant.strategy.rule.AssessmentRules;
import com.tradingassistant.strategy.rule.IndicatorRules;
import com.tradingassistant.strategy.rule.OptionsRules;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * Deterministic sample strategies expressed only as rule collections.
 *
 * <p>Every definition answers one question: are its conditions currently true?
 * None of them produces a trade direction, a score, a ranking or a
 * recommendation, and none of them depends on another strategy. Thresholds are
 * fixed defaults so an evaluation is reproducible; a caller that wants different
 * thresholds constructs its own definition from the same rule records.</p>
 */
public final class BuiltInStrategies {

    public static final BigDecimal ADX_THRESHOLD = new BigDecimal("20");
    public static final BigDecimal RSI_PULLBACK_CEILING = new BigDecimal("45");
    public static final long MINIMUM_VOLUME = 100_000L;
    public static final BigDecimal PCR_EXTREME_THRESHOLD = new BigDecimal("1.5");
    public static final BigDecimal MAX_PAIN_TOLERANCE_PERCENT = new BigDecimal("2");
    public static final BigDecimal EXPECTED_MOVE_PERCENT_OF_SPOT = new BigDecimal("1");

    private static final Set<StrategyInput> MARKET_INDICATOR_CANDLE = Set.of(
            StrategyInput.MARKET_ASSESSMENT, StrategyInput.INDICATORS, StrategyInput.CANDLE);
    private static final Set<StrategyInput> MARKET_INDICATORS = Set.of(
            StrategyInput.MARKET_ASSESSMENT, StrategyInput.INDICATORS);
    private static final Set<StrategyInput> MARKET_ONLY = Set.of(StrategyInput.MARKET_ASSESSMENT);
    private static final Set<StrategyInput> MARKET_OPTIONS = Set.of(
            StrategyInput.MARKET_ASSESSMENT, StrategyInput.OPTIONS_ASSESSMENT);

    private BuiltInStrategies() {
    }

    /**
     * Every built-in definition, in a stable order.
     */
    public static List<StrategyDefinition> all() {
        return List.of(
                emaTrend(),
                emaAdx(),
                supertrend(),
                vwapTrend(),
                rsiPullback(),
                macdMomentum(),
                bollingerExpansion(),
                breakout(),
                breakdown(),
                highVolume(),
                optionIvExpansion(),
                pcrExtreme(),
                maxPainProximity(),
                expectedMove(),
                liquidityFilter());
    }

    public static StrategyDefinition emaTrend() {
        return definition("EMA_TREND", "EMA Trend",
                "Close above the EMA of the current candle.",
                StrategyCategory.TREND, MARKET_INDICATOR_CANDLE,
                new IndicatorRules.CloseAboveIndicator("ema.close-above", "EMA"));
    }

    public static StrategyDefinition emaAdx() {
        return definition("EMA_ADX", "EMA with ADX",
                "Close above the EMA and ADX at or above the strength threshold.",
                StrategyCategory.TREND, MARKET_INDICATOR_CANDLE,
                new IndicatorRules.CloseAboveIndicator("ema.close-above", "EMA"),
                new IndicatorRules.IndicatorAtLeast("adx.at-least", "ADX", "adx",
                        ADX_THRESHOLD));
    }

    public static StrategyDefinition supertrend() {
        return definition("SUPERTREND", "Supertrend Direction",
                "Supertrend direction is positive.",
                StrategyCategory.TREND, MARKET_INDICATORS,
                new IndicatorRules.SupertrendBullish("supertrend.positive"));
    }

    public static StrategyDefinition vwapTrend() {
        return definition("VWAP_TREND", "VWAP Trend",
                "Close above the session VWAP.",
                StrategyCategory.INTRADAY, MARKET_INDICATOR_CANDLE,
                new IndicatorRules.CloseAboveIndicator("vwap.close-above", "VWAP"));
    }

    public static StrategyDefinition rsiPullback() {
        return definition("RSI_PULLBACK", "RSI Pullback",
                "RSI at or below the pullback ceiling.",
                StrategyCategory.REVERSAL, MARKET_INDICATORS,
                new IndicatorRules.IndicatorAtMost("rsi.at-most", "RSI", "value",
                        RSI_PULLBACK_CEILING));
    }

    public static StrategyDefinition macdMomentum() {
        return definition("MACD_MOMENTUM", "MACD Momentum",
                "MACD histogram is positive.",
                StrategyCategory.MOMENTUM, MARKET_INDICATORS,
                new IndicatorRules.MacdHistogramPositive("macd.histogram-positive"));
    }

    public static StrategyDefinition bollingerExpansion() {
        return definition("BOLLINGER_EXPANSION", "Bollinger Expansion",
                "Market volatility is described as expanding.",
                StrategyCategory.VOLATILITY, MARKET_ONLY,
                new AssessmentRules.MarketVolatilityState("volatility.expanding",
                        VolatilityState.EXPANDING));
    }

    public static StrategyDefinition breakout() {
        return definition("BREAKOUT", "Breakout",
                "Market structure reports a breakout candidate.",
                StrategyCategory.BREAKOUT, MARKET_ONLY,
                new AssessmentRules.StructureSignalPresent("structure.breakout",
                        StructureSignal.BREAKOUT_CANDIDATE));
    }

    public static StrategyDefinition breakdown() {
        return definition("BREAKDOWN", "Breakdown",
                "Market structure reports a breakdown candidate.",
                StrategyCategory.BREAKOUT, MARKET_ONLY,
                new AssessmentRules.StructureSignalPresent("structure.breakdown",
                        StructureSignal.BREAKDOWN_CANDIDATE));
    }

    public static StrategyDefinition highVolume() {
        return definition("HIGH_VOLUME", "High Volume",
                "Candle volume at or above the configured minimum.",
                StrategyCategory.INTRADAY, Set.of(StrategyInput.MARKET_ASSESSMENT,
                        StrategyInput.CANDLE),
                new AssessmentRules.VolumeAtLeast("volume.at-least", MINIMUM_VOLUME));
    }

    public static StrategyDefinition optionIvExpansion() {
        return definition("OPTION_IV_EXPANSION", "Option IV Expansion",
                "Option implied volatility is described as expanding.",
                StrategyCategory.OPTIONS, MARKET_OPTIONS,
                new OptionsRules.IvExpansion("iv.expanding"));
    }

    public static StrategyDefinition pcrExtreme() {
        return definition("PCR_EXTREME", "PCR Extreme",
                "Chain put/call ratio by open interest at or above the threshold.",
                StrategyCategory.OPTIONS, MARKET_OPTIONS,
                new OptionsRules.PcrAtLeast("pcr.at-least", PCR_EXTREME_THRESHOLD));
    }

    public static StrategyDefinition maxPainProximity() {
        return definition("MAX_PAIN_PROXIMITY", "Max Pain Proximity",
                "Max-pain strike within the configured distance of spot.",
                StrategyCategory.OPTIONS, MARKET_OPTIONS,
                new OptionsRules.MaxPainWithinPercent("maxpain.within",
                        MAX_PAIN_TOLERANCE_PERCENT));
    }

    public static StrategyDefinition expectedMove() {
        return definition("EXPECTED_MOVE", "Expected Move",
                "Expected expiry move at least the configured share of spot.",
                StrategyCategory.OPTIONS, MARKET_OPTIONS,
                new OptionsRules.ExpectedMoveAtLeastPercent("expected-move.at-least",
                        EXPECTED_MOVE_PERCENT_OF_SPOT));
    }

    public static StrategyDefinition liquidityFilter() {
        return definition("LIQUIDITY_FILTER", "Liquidity Filter",
                "Chain liquidity is described as liquid.",
                StrategyCategory.OPTIONS, MARKET_OPTIONS,
                new OptionsRules.LiquidityAtLeast("liquidity.liquid"));
    }

    private static StrategyDefinition definition(String id, String name, String description,
                                                 StrategyCategory category,
                                                 Set<StrategyInput> requiredInputs,
                                                 com.tradingassistant.strategy.api.StrategyRule... rules) {
        return new StrategyDefinition(id, name, description, true, category,
                requiredInputs, List.of(rules));
    }
}

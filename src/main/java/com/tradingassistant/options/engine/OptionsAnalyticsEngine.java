package com.tradingassistant.options.engine;

import com.tradingassistant.intelligence.domain.AssessmentStatus;
import com.tradingassistant.options.analysis.ExpectedMoveAnalyzer;
import com.tradingassistant.options.analysis.GreeksAnalyzer;
import com.tradingassistant.options.analysis.LiquidityAnalyzer;
import com.tradingassistant.options.analysis.MaxPainAnalyzer;
import com.tradingassistant.options.analysis.OpenInterestAnalyzer;
import com.tradingassistant.options.analysis.PutCallRatioAnalyzer;
import com.tradingassistant.options.config.OptionsAnalyticsConfig;
import com.tradingassistant.options.domain.ChainSnapshot;
import com.tradingassistant.options.domain.ChainSummary;
import com.tradingassistant.options.domain.ExpectedMoveSummary;
import com.tradingassistant.options.domain.GreeksSummary;
import com.tradingassistant.options.domain.LiquiditySummary;
import com.tradingassistant.options.domain.MaxPainSummary;
import com.tradingassistant.options.domain.OpenInterestSummary;
import com.tradingassistant.options.domain.OptionsAnalysisContext;
import com.tradingassistant.options.domain.OptionsAssessment;
import com.tradingassistant.options.domain.OptionLegKey;
import com.tradingassistant.options.domain.PutCallRatioSummary;
import com.tradingassistant.options.domain.VolatilitySummary;
import com.tradingassistant.options.greeks.GreeksEngine;
import com.tradingassistant.options.volatility.ImpliedVolatilityHistory;
import com.tradingassistant.options.volatility.ImpliedVolatilityResolver;
import com.tradingassistant.options.volatility.ImpliedVolatilitySolver;
import com.tradingassistant.options.volatility.VolatilityAnalyzer;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Composes the option analytics into one immutable assessment.
 *
 * <p>The engine is stateless and holds only its configuration and analyzers.
 * Each analyzer is independent and receives the same immutable context, so a
 * new description can be added without touching the others.</p>
 *
 * <p>The overall status is {@code READY} only when every section is ready,
 * {@code INSUFFICIENT_DATA} only when no section could be described, and
 * {@code PARTIAL} otherwise. There is no score and no direction.</p>
 */
public final class OptionsAnalyticsEngine {

    private static final int SECTION_COUNT = 8;

    private final OptionsAnalyticsConfig config;
    private final ImpliedVolatilityResolver resolver;
    private final VolatilityAnalyzer volatilityAnalyzer;
    private final GreeksAnalyzer greeksAnalyzer;
    private final OpenInterestAnalyzer openInterestAnalyzer;
    private final PutCallRatioAnalyzer putCallRatioAnalyzer;
    private final MaxPainAnalyzer maxPainAnalyzer;
    private final ExpectedMoveAnalyzer expectedMoveAnalyzer;
    private final LiquidityAnalyzer liquidityAnalyzer;

    public OptionsAnalyticsEngine(
            OptionsAnalyticsConfig config,
            ImpliedVolatilityResolver resolver,
            VolatilityAnalyzer volatilityAnalyzer,
            GreeksAnalyzer greeksAnalyzer,
            OpenInterestAnalyzer openInterestAnalyzer,
            PutCallRatioAnalyzer putCallRatioAnalyzer,
            MaxPainAnalyzer maxPainAnalyzer,
            ExpectedMoveAnalyzer expectedMoveAnalyzer,
            LiquidityAnalyzer liquidityAnalyzer) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.resolver = Objects.requireNonNull(resolver, "resolver must not be null");
        this.volatilityAnalyzer = Objects.requireNonNull(volatilityAnalyzer,
                "volatilityAnalyzer must not be null");
        this.greeksAnalyzer = Objects.requireNonNull(greeksAnalyzer,
                "greeksAnalyzer must not be null");
        this.openInterestAnalyzer = Objects.requireNonNull(openInterestAnalyzer,
                "openInterestAnalyzer must not be null");
        this.putCallRatioAnalyzer = Objects.requireNonNull(putCallRatioAnalyzer,
                "putCallRatioAnalyzer must not be null");
        this.maxPainAnalyzer = Objects.requireNonNull(maxPainAnalyzer,
                "maxPainAnalyzer must not be null");
        this.expectedMoveAnalyzer = Objects.requireNonNull(expectedMoveAnalyzer,
                "expectedMoveAnalyzer must not be null");
        this.liquidityAnalyzer = Objects.requireNonNull(liquidityAnalyzer,
                "liquidityAnalyzer must not be null");
    }

    public static OptionsAnalyticsEngine standard(OptionsAnalyticsConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        GreeksEngine greeksEngine = new GreeksEngine(config);
        return new OptionsAnalyticsEngine(config,
                new ImpliedVolatilityResolver(new ImpliedVolatilitySolver(config)),
                new VolatilityAnalyzer(config),
                new GreeksAnalyzer(greeksEngine),
                new OpenInterestAnalyzer(config),
                new PutCallRatioAnalyzer(config),
                new MaxPainAnalyzer(config),
                new ExpectedMoveAnalyzer(config),
                new LiquidityAnalyzer(config));
    }

    /**
     * Assesses a capture without implied-volatility history. Rank, percentile
     * and change are reported as unavailable.
     */
    public OptionsAssessment analyze(OptionsAnalysisContext context) {
        return analyze(context, null);
    }

    /**
     * Assesses a capture with the supplied volatility history, which is updated
     * with the current ATM observation.
     */
    public OptionsAssessment analyze(OptionsAnalysisContext context,
                                     ImpliedVolatilityHistory history) {
        Objects.requireNonNull(context, "context must not be null");
        ChainSnapshot snapshot = context.chain();
        Map<OptionLegKey, BigDecimal> impliedVolatilities = resolver.resolve(snapshot);

        ChainSummary chainSummary = chainSummary(snapshot);
        VolatilitySummary volatility = volatilityAnalyzer.analyze(snapshot,
                impliedVolatilities, history);
        GreeksSummary greeks = greeksAnalyzer.analyze(snapshot, impliedVolatilities);
        OpenInterestSummary openInterest = openInterestAnalyzer.analyze(snapshot,
                context.previousChain());
        PutCallRatioSummary putCallRatio = putCallRatioAnalyzer.analyze(snapshot);
        MaxPainSummary maxPain = maxPainAnalyzer.analyze(snapshot);
        ExpectedMoveSummary expectedMove = expectedMoveAnalyzer.analyze(snapshot,
                impliedVolatilities);
        LiquiditySummary liquidity = liquidityAnalyzer.analyze(snapshot);

        List<AssessmentStatus> sections = List.of(
                chainSummary.status(), volatility.status(), greeks.status(),
                openInterest.status(), putCallRatio.status(), expectedMove.status(),
                liquidity.status(), maxPain.status());
        long ready = sections.stream().filter(status -> status == AssessmentStatus.READY).count();
        long insufficient = sections.stream()
                .filter(status -> status == AssessmentStatus.INSUFFICIENT_DATA).count();
        AssessmentStatus status;
        if (ready == SECTION_COUNT) {
            status = AssessmentStatus.READY;
        } else if (insufficient == SECTION_COUNT) {
            status = AssessmentStatus.INSUFFICIENT_DATA;
        } else {
            status = AssessmentStatus.PARTIAL;
        }

        Map<String, String> diagnostics = new LinkedHashMap<>();
        diagnostics.put("model", GreeksEngine.class.getSimpleName());
        diagnostics.put("spot", snapshot.spotPrice().toPlainString());
        diagnostics.put("expiries", Integer.toString(snapshot.expiries().size()));
        diagnostics.put("strikes", Integer.toString(snapshot.chain().strikeCount()));
        diagnostics.put("legs", Integer.toString(snapshot.chain().legCount()));
        diagnostics.put("impliedVolatilities", Integer.toString(impliedVolatilities.size()));
        diagnostics.put("greeksEvaluated", Integer.toString(greeks.evaluatedLegs()));
        diagnostics.put("previousLegs", Integer.toString(context.previousChain().size()));
        diagnostics.put("atmDistancePercent", config.atmDistancePercent().toPlainString());
        diagnostics.put("sectionsReady", Long.toString(ready));

        return new OptionsAssessment(snapshot.spotInstrument(), snapshot.underlying(),
                snapshot.capturedAt(), status, status != AssessmentStatus.READY,
                chainSummary, volatility, greeks, openInterest, putCallRatio, expectedMove,
                liquidity, maxPain, context.marketAssessment().session(),
                Map.copyOf(diagnostics));
    }

    private ChainSummary chainSummary(ChainSnapshot snapshot) {
        String summary = snapshot.expiries().size() + " expiries, "
                + snapshot.chain().legCount() + " quoted legs, spot "
                + snapshot.spotPrice().toPlainString();
        return new ChainSummary(snapshot.nearestExpiryDate(), snapshot.expiries().size(),
                snapshot.chain().strikeCount(), snapshot.chain().legCount(),
                snapshot.spotPrice(), snapshot.status(), summary);
    }

    public OptionsAnalyticsConfig config() {
        return config;
    }
}

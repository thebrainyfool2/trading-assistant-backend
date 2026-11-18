package com.tradingassistant.intelligence.engine;

import com.tradingassistant.intelligence.analysis.MomentumAnalyzer;
import com.tradingassistant.intelligence.analysis.ParticipationAnalyzer;
import com.tradingassistant.intelligence.analysis.SessionContextAnalyzer;
import com.tradingassistant.intelligence.analysis.StructureAnalyzer;
import com.tradingassistant.intelligence.analysis.TrendAnalyzer;
import com.tradingassistant.intelligence.analysis.VolatilityAnalyzer;
import com.tradingassistant.intelligence.api.MarketAnalyzer;
import com.tradingassistant.intelligence.config.MarketIntelligenceConfig;
import com.tradingassistant.intelligence.domain.AnalysisContext;
import com.tradingassistant.intelligence.domain.AssessmentStatus;
import com.tradingassistant.intelligence.domain.Confidence;
import com.tradingassistant.intelligence.domain.ConfidenceLevel;
import com.tradingassistant.intelligence.domain.MarketAssessment;
import com.tradingassistant.intelligence.domain.MomentumAssessment;
import com.tradingassistant.intelligence.domain.ParticipationAssessment;
import com.tradingassistant.intelligence.domain.SessionAssessment;
import com.tradingassistant.intelligence.domain.StructureAssessment;
import com.tradingassistant.intelligence.domain.TrendAssessment;
import com.tradingassistant.intelligence.domain.VolatilityAssessment;
import com.tradingassistant.intelligence.exception.IntelligenceValidationException;

import java.util.List;
import java.util.Objects;

/**
 * Runs the independent analyzers and merges their assessments into one immutable
 * description of the market.
 *
 * <p>Composition lives here: analyzers never call each other, so an aspect can be
 * replaced, reordered or tested alone. The engine keeps no per-instrument state,
 * which makes it safe to share and trivial to replay.</p>
 *
 * <p>The engine rejects a context that lacks an indicator declared required by
 * the configuration. Missing optional inputs are reported through the aspect
 * status by the analyzers, never assumed.</p>
 */
public final class MarketIntelligenceEngine {

    private final MarketIntelligenceConfig config;
    private final MarketAnalyzer<TrendAssessment> trendAnalyzer;
    private final MarketAnalyzer<MomentumAssessment> momentumAnalyzer;
    private final MarketAnalyzer<VolatilityAssessment> volatilityAnalyzer;
    private final MarketAnalyzer<ParticipationAssessment> participationAnalyzer;
    private final MarketAnalyzer<StructureAssessment> structureAnalyzer;
    private final MarketAnalyzer<SessionAssessment> sessionAnalyzer;

    public MarketIntelligenceEngine(
            MarketIntelligenceConfig config,
            MarketAnalyzer<TrendAssessment> trendAnalyzer,
            MarketAnalyzer<MomentumAssessment> momentumAnalyzer,
            MarketAnalyzer<VolatilityAssessment> volatilityAnalyzer,
            MarketAnalyzer<ParticipationAssessment> participationAnalyzer,
            MarketAnalyzer<StructureAssessment> structureAnalyzer,
            MarketAnalyzer<SessionAssessment> sessionAnalyzer) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.trendAnalyzer = Objects.requireNonNull(trendAnalyzer, "trendAnalyzer must not be null");
        this.momentumAnalyzer = Objects.requireNonNull(momentumAnalyzer, "momentumAnalyzer must not be null");
        this.volatilityAnalyzer = Objects.requireNonNull(volatilityAnalyzer, "volatilityAnalyzer must not be null");
        this.participationAnalyzer = Objects.requireNonNull(
                participationAnalyzer, "participationAnalyzer must not be null");
        this.structureAnalyzer = Objects.requireNonNull(structureAnalyzer, "structureAnalyzer must not be null");
        this.sessionAnalyzer = Objects.requireNonNull(sessionAnalyzer, "sessionAnalyzer must not be null");
    }

    /**
     * Engine with the six standard analyzers and the supplied configuration.
     */
    public static MarketIntelligenceEngine standard(MarketIntelligenceConfig config) {
        return new MarketIntelligenceEngine(
                config,
                new TrendAnalyzer(),
                new MomentumAnalyzer(),
                new VolatilityAnalyzer(),
                new ParticipationAnalyzer(),
                new StructureAnalyzer(),
                new SessionContextAnalyzer());
    }

    public MarketIntelligenceConfig config() {
        return config;
    }

    public List<String> analyzerNames() {
        return List.of(
                trendAnalyzer.name(),
                momentumAnalyzer.name(),
                volatilityAnalyzer.name(),
                participationAnalyzer.name(),
                structureAnalyzer.name(),
                sessionAnalyzer.name());
    }

    /**
     * Describes one completed candle. The context is immutable, so the same
     * context and configuration always return an equal assessment.
     */
    public MarketAssessment assess(AnalysisContext context) {
        if (context == null) {
            throw new IntelligenceValidationException("Analysis context must not be null.");
        }
        validateRequiredIndicators(context);

        TrendAssessment trend = trendAnalyzer.analyze(context, config);
        MomentumAssessment momentum = momentumAnalyzer.analyze(context, config);
        VolatilityAssessment volatility = volatilityAnalyzer.analyze(context, config);
        ParticipationAssessment participation = participationAnalyzer.analyze(context, config);
        StructureAssessment structure = structureAnalyzer.analyze(context, config);
        SessionAssessment session = sessionAnalyzer.analyze(context, config);

        List<AssessmentStatus> marketStatuses = List.of(
                trend.status(), momentum.status(), volatility.status(),
                participation.status(), structure.status());
        AssessmentStatus status = overallStatus(marketStatuses, session.status());
        Confidence confidence = confidenceFor(status, marketStatuses);

        return new MarketAssessment(
                context.instrument(),
                context.interval(),
                context.latestCandle().timestamp(),
                status,
                status != AssessmentStatus.READY,
                confidence,
                trend,
                momentum,
                volatility,
                participation,
                structure,
                session);
    }

    private void validateRequiredIndicators(AnalysisContext context) {
        for (String required : config.requiredIndicators()) {
            if (!context.indicators().containsKey(required)) {
                throw new IntelligenceValidationException(
                        "Required indicator is missing from the analysis context: " + required);
            }
        }
    }

    /**
     * The assessment is ready only when every market aspect and the session
     * context are ready. It is insufficient when nothing about price behaviour
     * could be described, which is independent of knowing the session.
     */
    private static AssessmentStatus overallStatus(
            List<AssessmentStatus> marketStatuses,
            AssessmentStatus sessionStatus) {
        int ready = 0;
        int unavailable = 0;
        for (AssessmentStatus status : marketStatuses) {
            if (status == AssessmentStatus.READY) {
                ready++;
            } else if (status == AssessmentStatus.INSUFFICIENT_DATA
                    || status == AssessmentStatus.UNKNOWN) {
                unavailable++;
            }
        }
        if (ready == marketStatuses.size() && sessionStatus == AssessmentStatus.READY) {
            return AssessmentStatus.READY;
        }
        if (unavailable == marketStatuses.size()) {
            return AssessmentStatus.INSUFFICIENT_DATA;
        }
        return AssessmentStatus.PARTIAL;
    }

    private Confidence confidenceFor(AssessmentStatus overall, List<AssessmentStatus> marketStatuses) {
        int ready = 0;
        for (AssessmentStatus status : marketStatuses) {
            if (status == AssessmentStatus.READY) {
                ready++;
            }
        }
        String basis = ready + " of " + MarketIntelligenceConfig.MARKET_ASPECT_COUNT
                + " market aspects ready";
        if (ready == 0) {
            return Confidence.unknown(basis);
        }
        if (overall == AssessmentStatus.READY && ready >= config.highConfidenceAspectCount()) {
            return new Confidence(ConfidenceLevel.HIGH, basis);
        }
        if (ready >= config.mediumConfidenceAspectCount()) {
            return new Confidence(ConfidenceLevel.MEDIUM, basis);
        }
        return new Confidence(ConfidenceLevel.LOW, basis);
    }
}

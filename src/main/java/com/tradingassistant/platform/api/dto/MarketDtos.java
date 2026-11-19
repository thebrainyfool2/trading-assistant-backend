package com.tradingassistant.platform.api.dto;

import com.tradingassistant.intelligence.domain.AssessmentStatus;
import com.tradingassistant.intelligence.domain.MarketAssessment;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.market.domain.Segment;
import com.tradingassistant.options.domain.Greeks;
import com.tradingassistant.options.domain.OptionsAssessment;
import com.tradingassistant.qualification.domain.QualificationStatus;
import com.tradingassistant.qualification.domain.TradeQualification;
import com.tradingassistant.risk.domain.RiskAssessment;
import com.tradingassistant.risk.domain.RiskStatus;
import com.tradingassistant.strategy.domain.StrategyDefinition;
import com.tradingassistant.strategy.domain.StrategyEvaluation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Read-only descriptions produced by the analysis engine.
 *
 * <p>These are curated API views, not engine types. The engine keeps its own
 * immutable domain model and no calculation is repeated here; each field is a
 * direct projection of an existing assessment.</p>
 */
public final class MarketDtos {

    private MarketDtos() {
    }

    public record InstrumentView(Segment segment, String symbolToken) {

        public static InstrumentView from(InstrumentKey instrument) {
            return new InstrumentView(instrument.segment(), instrument.symbolToken());
        }
    }

    public record ComponentView(String state, String status, String summary) {
    }

    public record MarketAssessmentView(
            InstrumentView instrument,
            Interval interval,
            Instant timestamp,
            AssessmentStatus status,
            boolean warmup,
            String confidenceLevel,
            String confidenceBasis,
            ComponentView trend,
            ComponentView momentum,
            ComponentView volatility,
            ComponentView participation,
            String structureBias,
            List<String> structureSignals,
            String structureSummary,
            String sessionPhase,
            String sessionSummary) {

        public static MarketAssessmentView from(MarketAssessment assessment) {
            return new MarketAssessmentView(InstrumentView.from(assessment.instrument()),
                    assessment.interval(), assessment.timestamp(), assessment.status(),
                    assessment.warmup(), assessment.confidence().level().name(),
                    assessment.confidence().basis(),
                    new ComponentView(assessment.trend().state().name(),
                            assessment.trend().status().name(),
                            assessment.trend().summary()),
                    new ComponentView(assessment.momentum().state().name(),
                            assessment.momentum().status().name(),
                            assessment.momentum().summary()),
                    new ComponentView(assessment.volatility().state().name(),
                            assessment.volatility().status().name(),
                            assessment.volatility().summary()),
                    new ComponentView(assessment.participation().state().name(),
                            assessment.participation().status().name(),
                            assessment.participation().summary()),
                    assessment.structure().bias().name(),
                    assessment.structure().signals().stream().map(Enum::name).sorted()
                            .toList(),
                    assessment.structure().summary(),
                    assessment.session().phase().name(),
                    assessment.session().summary());
        }
    }

    public record OptionsAssessmentView(
            InstrumentView spotInstrument,
            String underlying,
            Instant timestamp,
            AssessmentStatus status,
            boolean warmup,
            BigDecimal spotPrice,
            LocalDate nearestExpiry,
            int expiryCount,
            int strikeCount,
            int legCount,
            BigDecimal atmImpliedVolatility,
            BigDecimal impliedVolatilityRank,
            BigDecimal impliedVolatilityPercentile,
            BigDecimal skew,
            String volatilityTrend,
            String volatilityState,
            BigDecimal netDelta,
            BigDecimal netGamma,
            BigDecimal netTheta,
            BigDecimal netVega,
            BigDecimal netRho,
            long totalCallOpenInterest,
            long totalPutOpenInterest,
            long totalCallVolume,
            long totalPutVolume,
            BigDecimal callConcentrationStrike,
            BigDecimal putConcentrationStrike,
            List<BigDecimal> supportZones,
            List<BigDecimal> resistanceZones,
            BigDecimal putCallOpenInterestRatio,
            BigDecimal putCallVolumeRatio,
            String expectedMoveMethod,
            LocalDate expectedMoveExpiry,
            BigDecimal expectedExpiryMove,
            BigDecimal expectedDailyMove,
            BigDecimal expectedUpperBound,
            BigDecimal expectedLowerBound,
            String liquidityState,
            String tradability,
            BigDecimal averageSpreadPercent,
            LocalDate maxPainExpiry,
            BigDecimal maxPainStrike,
            String sessionPhase,
            Map<String, String> diagnostics,
            String summary) {

        public static OptionsAssessmentView from(OptionsAssessment assessment) {
            Greeks net = assessment.greeks().netExposure();
            return new OptionsAssessmentView(
                    InstrumentView.from(assessment.spotInstrument()),
                    assessment.underlying(), assessment.timestamp(), assessment.status(),
                    assessment.warmup(), assessment.chain().spotPrice(),
                    assessment.chain().nearestExpiry().orElse(null),
                    assessment.chain().expiryCount(), assessment.chain().strikeCount(),
                    assessment.chain().legCount(),
                    assessment.volatility().atmImpliedVolatility().orElse(null),
                    assessment.volatility().impliedVolatilityRank().orElse(null),
                    assessment.volatility().impliedVolatilityPercentile().orElse(null),
                    assessment.volatility().skew().orElse(null),
                    assessment.volatility().trend().name(),
                    assessment.volatility().state().name(),
                    net.delta(), net.gamma(), net.theta(), net.vega(), net.rho(),
                    assessment.openInterest().totalCallOpenInterest(),
                    assessment.openInterest().totalPutOpenInterest(),
                    assessment.openInterest().totalCallVolume(),
                    assessment.openInterest().totalPutVolume(),
                    assessment.openInterest().callConcentrationStrike().orElse(null),
                    assessment.openInterest().putConcentrationStrike().orElse(null),
                    assessment.openInterest().supportZones(),
                    assessment.openInterest().resistanceZones(),
                    assessment.putCallRatio().chainOpenInterestRatio().orElse(null),
                    assessment.putCallRatio().chainVolumeRatio().orElse(null),
                    assessment.expectedMove().method().name(),
                    assessment.expectedMove().expiry().orElse(null),
                    assessment.expectedMove().expiryMove().orElse(null),
                    assessment.expectedMove().dailyMove().orElse(null),
                    assessment.expectedMove().upperBound().orElse(null),
                    assessment.expectedMove().lowerBound().orElse(null),
                    assessment.liquidity().state().name(),
                    assessment.liquidity().tradability().name(),
                    assessment.liquidity().averageSpreadPercent().orElse(null),
                    assessment.maxPain().expiry().orElse(null),
                    assessment.maxPain().maxPainStrike().orElse(null),
                    assessment.session().phase().name(), assessment.diagnostics(),
                    assessment.chain().summary() + ' ' + assessment.volatility().summary());
        }
    }

    public record RuleView(String ruleId, String status, String reason) {
    }

    public record StrategyEvaluationView(
            String strategyId,
            String strategyName,
            InstrumentView instrument,
            Interval interval,
            Instant timestamp,
            String status,
            String summary,
            List<RuleView> rules) {

        public static StrategyEvaluationView from(StrategyEvaluation evaluation) {
            return new StrategyEvaluationView(evaluation.strategyId(),
                    evaluation.strategyName(), InstrumentView.from(evaluation.instrument()),
                    evaluation.interval(), evaluation.timestamp(),
                    evaluation.status().name(), evaluation.summary(),
                    evaluation.ruleEvaluations().stream()
                            .map(rule -> new RuleView(rule.ruleId(),
                                    rule.status().name(), rule.reason()))
                            .toList());
        }
    }

    public record RiskAssessmentView(
            String strategyId,
            String strategyName,
            InstrumentView instrument,
            Interval interval,
            Instant timestamp,
            RiskStatus status,
            String qualificationStatus,
            BigDecimal riskPerTrade,
            BigDecimal maximumPermittedLoss,
            String stopMethodology,
            BigDecimal stopDistance,
            BigDecimal stopDistancePercent,
            BigDecimal quantity,
            BigDecimal positionValue,
            BigDecimal requiredLeverage,
            BigDecimal referencePrice,
            BigDecimal minimumRiskReward,
            BigDecimal achievedRiskReward,
            BigDecimal maximumExposure,
            BigDecimal plannedExposure,
            BigDecimal dailyLossLimit,
            BigDecimal plannedRisk,
            String summary,
            List<RuleView> rules) {

        public static RiskAssessmentView from(RiskAssessment assessment) {
            return new RiskAssessmentView(assessment.strategyId(),
                    assessment.strategyName(), InstrumentView.from(assessment.instrument()),
                    assessment.interval(), assessment.timestamp(), assessment.status(),
                    assessment.qualificationStatus().name(), assessment.riskPerTrade(),
                    assessment.maximumPermittedLoss(),
                    assessment.stop().methodology().name(),
                    assessment.stop().distance(), assessment.stop().distancePercent(),
                    assessment.sizing().quantity(), assessment.sizing().positionValue(),
                    assessment.sizing().requiredLeverage(),
                    assessment.sizing().referencePrice(),
                    assessment.riskReward().minimumRatio(),
                    assessment.riskReward().achievedRatio().orElse(null),
                    assessment.exposure().maximumExposure(),
                    assessment.exposure().plannedExposure().orElse(null),
                    assessment.dailyLoss().dailyLimit(),
                    assessment.dailyLoss().plannedRisk().orElse(null),
                    assessment.summary(),
                    assessment.ruleEvaluations().stream()
                            .map(rule -> new RuleView(rule.ruleId(),
                                    rule.status().name(), rule.reason()))
                            .toList());
        }
    }

    public record QualificationView(
            String strategyId,
            String strategyName,
            InstrumentView instrument,
            Interval interval,
            Instant timestamp,
            QualificationStatus status,
            String summary,
            List<RuleView> rules) {

        public static QualificationView from(TradeQualification qualification) {
            return new QualificationView(qualification.strategyId(),
                    qualification.strategyName(),
                    InstrumentView.from(qualification.instrument()),
                    qualification.interval(), qualification.timestamp(),
                    qualification.status(), qualification.summary(),
                    qualification.ruleEvaluations().stream()
                            .map(rule -> new RuleView(rule.ruleId(),
                                    rule.status().name(), rule.reason()))
                            .toList());
        }
    }

    public record StrategyDefinitionView(
            String id,
            String name,
            String description,
            boolean enabled,
            String category,
            int ruleCount,
            List<String> requiredInputs) {

        public static StrategyDefinitionView from(StrategyDefinition definition) {
            return new StrategyDefinitionView(definition.id(), definition.name(),
                    definition.description(), definition.enabled(),
                    definition.category().name(), definition.ruleCount(),
                    definition.requiredInputs().stream().map(Enum::name).sorted()
                            .toList());
        }
    }

    public record MarketOverviewView(
            Instant generatedAt,
            boolean engineAvailable,
            int readModelEntries,
            List<MarketAssessmentView> markets,
            List<OptionsAssessmentView> options,
            List<StrategyEvaluationView> strategies,
            List<RiskAssessmentView> risks,
            List<QualificationView> qualifications) {
    }
}

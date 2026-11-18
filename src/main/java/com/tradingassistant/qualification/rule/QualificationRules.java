package com.tradingassistant.qualification.rule;

import com.tradingassistant.intelligence.domain.AssessmentStatus;
import com.tradingassistant.intelligence.domain.ConfidenceLevel;
import com.tradingassistant.intelligence.domain.ParticipationState;
import com.tradingassistant.options.domain.LiquidityState;
import com.tradingassistant.qualification.api.QualificationRule;
import com.tradingassistant.qualification.domain.QualificationKey;
import com.tradingassistant.qualification.domain.QualificationLedger;
import com.tradingassistant.qualification.domain.QualificationRuleEvaluation;
import com.tradingassistant.qualification.domain.TradeQualificationContext;
import com.tradingassistant.qualification.exception.QualificationValidationException;
import com.tradingassistant.strategy.domain.EvaluationStatus;

import java.util.Objects;

/**
 * Immutable built-in qualification rules.
 *
 * <p>Every rule is a record holding only its configuration, so one instance can be
 * reused across every qualification. Each rule inspects the same immutable context
 * and reports one outcome; no rule reads or writes another rule's result. A rule
 * that cannot decide reports {@code INSUFFICIENT_DATA} with a field-specific
 * reason rather than assuming a value.</p>
 */
public final class QualificationRules {

    private QualificationRules() {
    }

    /**
     * Condition: the exchange session is one in which qualification is meaningful.
     */
    public record SessionOpen(String id) implements QualificationRule {

        public SessionOpen {
            id = Rules.requireId(id);
        }

        @Override
        public QualificationRuleEvaluation evaluate(TradeQualificationContext context) {
            var session = Rules.requireContext(context).session();
            if (session.status() != AssessmentStatus.READY) {
                return QualificationRuleEvaluation.insufficient(id,
                        "session status is " + QualificationSupport.name(session.status()));
            }
            var metadata = QualificationSupport.metadata("phase",
                    QualificationSupport.name(session.phase()));
            return switch (session.phase()) {
                case OPENING, REGULAR, CLOSING -> QualificationRuleEvaluation.passed(id,
                        "session phase " + QualificationSupport.name(session.phase())
                                + " is open", metadata);
                case PRE_OPEN, AFTER_MARKET, UNKNOWN -> QualificationRuleEvaluation.failed(id,
                        "session phase " + QualificationSupport.name(session.phase())
                                + " is not eligible", metadata);
            };
        }
    }

    /**
     * Condition: the market assessment was produced with every input ready.
     */
    public record MarketAssessmentReady(String id) implements QualificationRule {

        public MarketAssessmentReady {
            id = Rules.requireId(id);
        }

        @Override
        public QualificationRuleEvaluation evaluate(TradeQualificationContext context) {
            var market = Rules.requireContext(context).marketAssessment();
            var metadata = QualificationSupport.metadata("status",
                    QualificationSupport.name(market.status()));
            return switch (market.status()) {
                case READY -> QualificationRuleEvaluation.passed(id,
                        "market assessment is ready", metadata);
                case PARTIAL -> QualificationRuleEvaluation.failed(id,
                        "market assessment is PARTIAL", metadata);
                case INSUFFICIENT_DATA, UNKNOWN -> QualificationRuleEvaluation.insufficient(id,
                        "market assessment status is "
                                + QualificationSupport.name(market.status()));
            };
        }
    }

    /**
     * Condition: an option assessment is available whenever one is required.
     */
    public record OptionsRequirement(String id, boolean required) implements QualificationRule {

        public OptionsRequirement {
            id = Rules.requireId(id);
        }

        @Override
        public QualificationRuleEvaluation evaluate(TradeQualificationContext context) {
            var value = Rules.requireContext(context);
            if (value.optionsAssessment().isEmpty()) {
                if (required) {
                    return QualificationRuleEvaluation.insufficient(id,
                            "options assessment is required but unavailable");
                }
                return QualificationRuleEvaluation.passed(id,
                        "options assessment is not required",
                        QualificationSupport.metadata("required", "false"));
            }
            var options = value.optionsAssessment().get();
            var metadata = QualificationSupport.metadata("status",
                    QualificationSupport.name(options.status()));
            if (options.status() == AssessmentStatus.READY) {
                return QualificationRuleEvaluation.passed(id,
                        "options assessment is ready", metadata);
            }
            return QualificationRuleEvaluation.failed(id,
                    "options assessment status is "
                            + QualificationSupport.name(options.status()), metadata);
        }
    }

    /**
     * Condition: a present option assessment describes the same instant as the
     * strategy evaluation.
     */
    public record OptionsFreshness(String id, boolean required) implements QualificationRule {

        public OptionsFreshness {
            id = Rules.requireId(id);
        }

        @Override
        public QualificationRuleEvaluation evaluate(TradeQualificationContext context) {
            var value = Rules.requireContext(context);
            if (value.optionsAssessment().isEmpty()) {
                if (required) {
                    return QualificationRuleEvaluation.insufficient(id,
                            "options assessment is required but unavailable");
                }
                return QualificationRuleEvaluation.passed(id,
                        "options assessment is not required",
                        QualificationSupport.metadata("required", "false"));
            }
            var options = value.optionsAssessment().get();
            var metadata = QualificationSupport.metadata(
                    "optionsTimestamp", QualificationSupport.instant(options.timestamp()),
                    "timestamp", QualificationSupport.instant(value.timestamp()));
            if (options.timestamp().equals(value.timestamp())) {
                return QualificationRuleEvaluation.passed(id,
                        "options assessment describes the evaluated instant", metadata);
            }
            return QualificationRuleEvaluation.failed(id,
                    "options assessment timestamp does not match the evaluated instant",
                    metadata);
        }
    }

    /**
     * Condition: a present option assessment was produced in the same session phase
     * as the market assessment.
     */
    public record SessionAlignment(String id, boolean required) implements QualificationRule {

        public SessionAlignment {
            id = Rules.requireId(id);
        }

        @Override
        public QualificationRuleEvaluation evaluate(TradeQualificationContext context) {
            var value = Rules.requireContext(context);
            if (value.optionsAssessment().isEmpty()) {
                if (required) {
                    return QualificationRuleEvaluation.insufficient(id,
                            "options assessment is required but unavailable");
                }
                return QualificationRuleEvaluation.passed(id,
                        "options assessment is not required",
                        QualificationSupport.metadata("required", "false"));
            }
            var options = value.optionsAssessment().get();
            if (options.session().status() != AssessmentStatus.READY) {
                return QualificationRuleEvaluation.insufficient(id,
                        "options session status is "
                                + QualificationSupport.name(options.session().status()));
            }
            var marketPhase = value.session().phase();
            var optionsPhase = options.session().phase();
            var metadata = QualificationSupport.metadata(
                    "marketPhase", QualificationSupport.name(marketPhase),
                    "optionsPhase", QualificationSupport.name(optionsPhase));
            if (marketPhase == optionsPhase) {
                return QualificationRuleEvaluation.passed(id,
                        "market and option assessments share the session phase", metadata);
            }
            return QualificationRuleEvaluation.failed(id,
                    "market and option assessments disagree on the session phase", metadata);
        }
    }

    /**
     * Condition: the strategy evaluation is not in the {@code UNKNOWN} state.
     */
    public record StrategyStatusKnown(String id) implements QualificationRule {

        public StrategyStatusKnown {
            id = Rules.requireId(id);
        }

        @Override
        public QualificationRuleEvaluation evaluate(TradeQualificationContext context) {
            var evaluation = Rules.requireContext(context).strategyEvaluation();
            var metadata = QualificationSupport.metadata("status",
                    QualificationSupport.name(evaluation.status()));
            if (evaluation.status() == EvaluationStatus.UNKNOWN) {
                return QualificationRuleEvaluation.failed(id,
                        "strategy status is UNKNOWN", metadata);
            }
            return QualificationRuleEvaluation.passed(id,
                    "strategy status is " + QualificationSupport.name(evaluation.status()),
                    metadata);
        }
    }

    /**
     * Condition: the strategy evaluation itself passed every strategy rule.
     */
    public record StrategyStatusPassed(String id) implements QualificationRule {

        public StrategyStatusPassed {
            id = Rules.requireId(id);
        }

        @Override
        public QualificationRuleEvaluation evaluate(TradeQualificationContext context) {
            var evaluation = Rules.requireContext(context).strategyEvaluation();
            var metadata = QualificationSupport.metadata("status",
                    QualificationSupport.name(evaluation.status()));
            return switch (evaluation.status()) {
                case PASSED -> QualificationRuleEvaluation.passed(id,
                        "strategy evaluation passed", metadata);
                case INSUFFICIENT_DATA -> QualificationRuleEvaluation.insufficient(id,
                        "strategy status is INSUFFICIENT_DATA");
                case FAILED, PARTIAL, UNKNOWN -> QualificationRuleEvaluation.failed(id,
                        "strategy status is " + QualificationSupport.name(evaluation.status()),
                        metadata);
            };
        }
    }

    /**
     * Condition: the market confidence level reaches the configured minimum.
     */
    public record MinimumMarketConfidence(String id, ConfidenceLevel minimum)
            implements QualificationRule {

        public MinimumMarketConfidence {
            id = Rules.requireId(id);
            minimum = Rules.requireConfidence(minimum);
        }

        @Override
        public QualificationRuleEvaluation evaluate(TradeQualificationContext context) {
            var confidence = Rules.requireContext(context).marketAssessment().confidence();
            var metadata = QualificationSupport.metadata(
                    "level", QualificationSupport.name(confidence.level()),
                    "minimum", QualificationSupport.name(minimum));
            if (confidence.level() == ConfidenceLevel.UNKNOWN) {
                return QualificationRuleEvaluation.insufficient(id,
                        "market confidence level is UNKNOWN");
            }
            if (QualificationSupport.confidenceRank(confidence.level())
                    >= QualificationSupport.confidenceRank(minimum)) {
                return QualificationRuleEvaluation.passed(id,
                        "market confidence reaches the configured minimum", metadata);
            }
            return QualificationRuleEvaluation.failed(id,
                    "market confidence is below the configured minimum", metadata);
        }
    }

    /**
     * Condition: participation is at least the configured level, so a condition
     * observed on almost no volume is not qualified.
     */
    public record MinimumParticipation(String id, ParticipationState minimum)
            implements QualificationRule {

        public MinimumParticipation {
            id = Rules.requireId(id);
            minimum = Rules.requireParticipation(minimum);
        }

        @Override
        public QualificationRuleEvaluation evaluate(TradeQualificationContext context) {
            var participation = Rules.requireContext(context).marketAssessment().participation();
            var metadata = QualificationSupport.metadata(
                    "state", QualificationSupport.name(participation.state()),
                    "minimum", QualificationSupport.name(minimum));
            if (participation.state() == ParticipationState.UNKNOWN) {
                return QualificationRuleEvaluation.insufficient(id,
                        "market participation is UNKNOWN");
            }
            if (QualificationSupport.participationRank(participation.state())
                    >= QualificationSupport.participationRank(minimum)) {
                return QualificationRuleEvaluation.passed(id,
                        "market participation reaches the configured minimum", metadata);
            }
            return QualificationRuleEvaluation.failed(id,
                    "market participation is below the configured minimum", metadata);
        }
    }

    /**
     * Condition: a present option assessment reports usable liquidity.
     */
    public record OptionLiquidity(String id, boolean required) implements QualificationRule {

        public OptionLiquidity {
            id = Rules.requireId(id);
        }

        @Override
        public QualificationRuleEvaluation evaluate(TradeQualificationContext context) {
            var value = Rules.requireContext(context);
            if (value.optionsAssessment().isEmpty()) {
                if (required) {
                    return QualificationRuleEvaluation.insufficient(id,
                            "options assessment is required but unavailable");
                }
                return QualificationRuleEvaluation.passed(id,
                        "options assessment is not required",
                        QualificationSupport.metadata("required", "false"));
            }
            var liquidity = value.optionsAssessment().get().liquidity();
            var metadata = QualificationSupport.metadata("state",
                    QualificationSupport.name(liquidity.state()));
            return switch (liquidity.state()) {
                case LIQUID, MODERATE -> QualificationRuleEvaluation.passed(id,
                        "option liquidity is " + QualificationSupport.name(liquidity.state()),
                        metadata);
                case ILLIQUID -> QualificationRuleEvaluation.failed(id,
                        "option liquidity is ILLIQUID", metadata);
                case UNKNOWN -> QualificationRuleEvaluation.insufficient(id,
                        "option liquidity is UNKNOWN");
            };
        }
    }

    /**
     * Condition: this strategy, instrument and instant has not already been
     * qualified.
     *
     * <p>The rule holds a final reference to a bounded ledger owned by the service;
     * it never writes to it. Direct engine callers can supply their own ledger.</p>
     */
    public record DuplicateQualification(String id, QualificationLedger ledger)
            implements QualificationRule {

        public DuplicateQualification {
            id = Rules.requireId(id);
            ledger = Objects.requireNonNull(ledger, "ledger must not be null");
        }

        @Override
        public QualificationRuleEvaluation evaluate(TradeQualificationContext context) {
            var key = QualificationKey.of(Rules.requireContext(context));
            var metadata = QualificationSupport.metadata(
                    "strategyId", key.strategyId(),
                    "timestamp", QualificationSupport.instant(key.timestamp()));
            if (ledger.contains(key)) {
                return QualificationRuleEvaluation.failed(id,
                        "already qualified for this instrument and timestamp", metadata);
            }
            return QualificationRuleEvaluation.passed(id,
                    "no previous qualification for this instrument and timestamp", metadata);
        }
    }

    /**
     * Shared validation used by every rule record in this package.
     */
    static final class Rules {

        private Rules() {
        }

        static String requireId(String value) {
            if (value == null || value.isBlank()) {
                throw new QualificationValidationException("rule id must not be blank");
            }
            return value.trim();
        }

        static TradeQualificationContext requireContext(TradeQualificationContext context) {
            if (context == null) {
                throw new QualificationValidationException("context must not be null");
            }
            return context;
        }

        static ConfidenceLevel requireConfidence(ConfidenceLevel value) {
            if (value == null) {
                throw new QualificationValidationException("minimum confidence must not be null");
            }
            if (value == ConfidenceLevel.UNKNOWN) {
                throw new QualificationValidationException(
                        "minimum confidence must be a concrete level");
            }
            return value;
        }

        static ParticipationState requireParticipation(ParticipationState value) {
            if (value == null) {
                throw new QualificationValidationException(
                        "minimum participation must not be null");
            }
            if (value == ParticipationState.UNKNOWN) {
                throw new QualificationValidationException(
                        "minimum participation must be a concrete state");
            }
            return value;
        }
    }
}

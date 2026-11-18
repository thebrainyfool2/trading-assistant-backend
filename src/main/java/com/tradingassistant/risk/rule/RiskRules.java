package com.tradingassistant.risk.rule;

import com.tradingassistant.intelligence.domain.VolatilityState;
import com.tradingassistant.qualification.domain.QualificationStatus;
import com.tradingassistant.risk.api.RiskRule;
import com.tradingassistant.risk.config.RiskEngineConfig;
import com.tradingassistant.risk.domain.RiskRuleContext;
import com.tradingassistant.risk.domain.RiskRuleEvaluation;
import com.tradingassistant.risk.exception.RiskValidationException;
import com.tradingassistant.risk.math.RiskNumbers;

import java.math.BigDecimal;

/**
 * Immutable built-in risk rules.
 *
 * <p>Every rule is a record holding only its identifier, so one instance can serve
 * every assessment. Each rule inspects the same immutable inputs, derived plan and
 * policy, and reports one boundary outcome. A rule that cannot decide reports
 * {@code INSUFFICIENT_DATA} with a field-specific reason rather than assuming a
 * value.</p>
 */
public final class RiskRules {

    /** Fraction of ATR a stop must clear so normal gaps and noise stay outside it. */
    public static final BigDecimal GAP_PROTECTION_ATR_FLOOR = new BigDecimal("0.5");

    private RiskRules() {
    }

    /**
     * Boundary: only a qualified strategy evaluation can be assessed.
     */
    public record QualificationEligible(String id) implements RiskRule {

        public QualificationEligible {
            id = Rules.requireId(id);
        }

        @Override
        public RiskRuleEvaluation evaluate(RiskRuleContext context) {
            QualificationStatus status = Rules.requireContext(context)
                    .context().qualification().status();
            var metadata = RiskNumbers.metadata("qualificationStatus", status.name());
            return switch (status) {
                case QUALIFIED -> RiskRuleEvaluation.passed(id,
                        "qualification is eligible", metadata);
                case DISQUALIFIED -> RiskRuleEvaluation.failed(id,
                        "qualification is DISQUALIFIED", metadata);
                case PARTIAL -> RiskRuleEvaluation.failed(id,
                        "qualification is PARTIAL", metadata);
                case INSUFFICIENT_DATA, UNKNOWN -> RiskRuleEvaluation.insufficient(id,
                        "qualification status is " + status.name());
            };
        }
    }

    /**
     * Boundary: a positive ATR exists and a stop distance was derived from it.
     */
    public record AtrStopValidation(String id) implements RiskRule {

        public AtrStopValidation {
            id = Rules.requireId(id);
        }

        @Override
        public RiskRuleEvaluation evaluate(RiskRuleContext context) {
            Rules.requireContext(context);
            var plan = context.plan();
            if (plan.atr().isEmpty()) {
                return RiskRuleEvaluation.insufficient(id, "ATR value is unavailable");
            }
            BigDecimal atr = plan.atr().get();
            if (atr.signum() <= 0) {
                return RiskRuleEvaluation.failed(id, "ATR must be greater than zero",
                        RiskNumbers.metadata("atr", RiskNumbers.format(atr)));
            }
            if (!plan.stopAvailable()) {
                return RiskRuleEvaluation.insufficient(id,
                        "stop distance could not be derived");
            }
            return RiskRuleEvaluation.passed(id,
                    "stop distance was derived from ATR",
                    RiskNumbers.metadata("atr", RiskNumbers.format(atr),
                            "atrMultiple", RiskNumbers.format(plan.atrMultiple()),
                            "distance", RiskNumbers.format(plan.stopDistance())));
        }
    }

    /**
     * Boundary: the derived stop is not tighter than the configured minimum.
     */
    public record MinimumStopDistance(String id) implements RiskRule {

        public MinimumStopDistance {
            id = Rules.requireId(id);
        }

        @Override
        public RiskRuleEvaluation evaluate(RiskRuleContext context) {
            Rules.requireContext(context);
            if (!context.plan().stopAvailable()) {
                return RiskRuleEvaluation.insufficient(id, "stop distance is unavailable");
            }
            BigDecimal distance = context.plan().stopDistance();
            BigDecimal minimum = context.config().minimumStopDistance();
            var metadata = RiskNumbers.metadata("distance", RiskNumbers.format(distance),
                    "minimum", RiskNumbers.format(minimum));
            if (distance.compareTo(minimum) >= 0) {
                return RiskRuleEvaluation.passed(id,
                        "stop distance is at least the configured minimum", metadata);
            }
            return RiskRuleEvaluation.failed(id,
                    "stop distance is below the configured minimum", metadata);
        }
    }

    /**
     * Boundary: the derived stop is not wider than the configured maximum.
     */
    public record MaximumStopDistance(String id) implements RiskRule {

        public MaximumStopDistance {
            id = Rules.requireId(id);
        }

        @Override
        public RiskRuleEvaluation evaluate(RiskRuleContext context) {
            Rules.requireContext(context);
            if (!context.plan().stopAvailable()) {
                return RiskRuleEvaluation.insufficient(id, "stop distance is unavailable");
            }
            BigDecimal distance = context.plan().stopDistance();
            BigDecimal maximum = context.config().maximumStopDistance();
            var metadata = RiskNumbers.metadata("distance", RiskNumbers.format(distance),
                    "maximum", RiskNumbers.format(maximum));
            if (distance.compareTo(maximum) <= 0) {
                return RiskRuleEvaluation.passed(id,
                        "stop distance is at most the configured maximum", metadata);
            }
            return RiskRuleEvaluation.failed(id,
                    "stop distance exceeds the configured maximum", metadata);
        }
    }

    /**
     * Boundary: the volatility adjustment was resolved and stays inside a sane band.
     */
    public record VolatilityMultiplier(String id) implements RiskRule {

        public VolatilityMultiplier {
            id = Rules.requireId(id);
        }

        @Override
        public RiskRuleEvaluation evaluate(RiskRuleContext context) {
            Rules.requireContext(context);
            VolatilityState state = context.context().marketAssessment().volatility().state();
            BigDecimal adjustment = context.plan().volatilityAdjustment();
            var metadata = RiskNumbers.metadata("state", state.name(),
                    "adjustment", RiskNumbers.format(adjustment),
                    "atrMultiple", RiskNumbers.format(context.plan().atrMultiple()));
            if (state == VolatilityState.UNKNOWN) {
                return RiskRuleEvaluation.insufficient(id,
                        "market volatility state is UNKNOWN");
            }
            if (adjustment.compareTo(new BigDecimal("0.5")) < 0
                    || adjustment.compareTo(new BigDecimal("2")) > 0) {
                return RiskRuleEvaluation.failed(id,
                        "volatility adjustment is outside the permitted band", metadata);
            }
            return RiskRuleEvaluation.passed(id,
                    "volatility adjustment was applied", metadata);
        }
    }

    /**
     * Boundary: the risk budget respects the configured per-trade percentage.
     */
    public record MaximumRiskPercent(String id) implements RiskRule {

        public MaximumRiskPercent {
            id = Rules.requireId(id);
        }

        @Override
        public RiskRuleEvaluation evaluate(RiskRuleContext context) {
            Rules.requireContext(context);
            var plan = context.plan();
            BigDecimal limit = RiskEngineConfig.percentOf(plan.capital(),
                    context.config().maximumRiskPercent());
            var metadata = RiskNumbers.metadata(
                    "riskPercent", RiskNumbers.format(plan.riskPercent()),
                    "maximumRiskPercent", RiskNumbers.format(
                            context.config().maximumRiskPercent()),
                    "riskAmount", RiskNumbers.format(plan.riskAmount()),
                    "limit", RiskNumbers.format(limit));
            if (plan.riskPercent().compareTo(context.config().maximumRiskPercent()) > 0) {
                return RiskRuleEvaluation.failed(id,
                        "risk percentage exceeds the configured maximum", metadata);
            }
            if (plan.riskAmount().compareTo(limit) > 0) {
                return RiskRuleEvaluation.failed(id,
                        "risk amount exceeds the configured maximum", metadata);
            }
            if (plan.riskAmount().signum() <= 0) {
                return RiskRuleEvaluation.insufficient(id, "risk amount is unavailable");
            }
            return RiskRuleEvaluation.passed(id,
                    "risk amount is within the configured maximum", metadata);
        }
    }

    /**
     * Boundary: the planned risk stays inside the daily loss limit.
     */
    public record DailyRiskLimit(String id) implements RiskRule {

        public DailyRiskLimit {
            id = Rules.requireId(id);
        }

        @Override
        public RiskRuleEvaluation evaluate(RiskRuleContext context) {
            Rules.requireContext(context);
            BigDecimal risk = context.plan().riskAmount();
            BigDecimal limit = context.config().dailyLossLimit();
            var metadata = RiskNumbers.metadata("plannedRisk", RiskNumbers.format(risk),
                    "dailyLimit", RiskNumbers.format(limit));
            if (risk.signum() <= 0) {
                return RiskRuleEvaluation.insufficient(id, "risk amount is unavailable");
            }
            if (risk.compareTo(limit) <= 0) {
                return RiskRuleEvaluation.passed(id,
                        "planned risk is inside the daily loss limit", metadata);
            }
            return RiskRuleEvaluation.failed(id,
                    "planned risk exceeds the daily loss limit", metadata);
        }
    }

    /**
     * Boundary: the planned risk stays inside the session loss cap.
     */
    public record SessionLossCap(String id) implements RiskRule {

        public SessionLossCap {
            id = Rules.requireId(id);
        }

        @Override
        public RiskRuleEvaluation evaluate(RiskRuleContext context) {
            Rules.requireContext(context);
            BigDecimal risk = context.plan().riskAmount();
            BigDecimal limit = context.config().sessionLossLimit();
            var metadata = RiskNumbers.metadata("plannedRisk", RiskNumbers.format(risk),
                    "sessionLimit", RiskNumbers.format(limit));
            if (risk.signum() <= 0) {
                return RiskRuleEvaluation.insufficient(id, "risk amount is unavailable");
            }
            if (risk.compareTo(limit) <= 0) {
                return RiskRuleEvaluation.passed(id,
                        "planned risk is inside the session loss cap", metadata);
            }
            return RiskRuleEvaluation.failed(id,
                    "planned risk exceeds the session loss cap", metadata);
        }
    }

    /**
     * Boundary: the computed position value stays inside the open exposure limit.
     */
    public record MaximumOpenExposure(String id) implements RiskRule {

        public MaximumOpenExposure {
            id = Rules.requireId(id);
        }

        @Override
        public RiskRuleEvaluation evaluate(RiskRuleContext context) {
            Rules.requireContext(context);
            if (!context.plan().sizingAvailable()) {
                return RiskRuleEvaluation.insufficient(id, "position size is unavailable");
            }
            BigDecimal exposure = context.plan().positionValue();
            BigDecimal maximum = context.config().maximumExposure();
            var metadata = RiskNumbers.metadata("exposure", RiskNumbers.format(exposure),
                    "maximum", RiskNumbers.format(maximum));
            if (exposure.compareTo(maximum) <= 0) {
                return RiskRuleEvaluation.passed(id,
                        "open exposure is inside the configured maximum", metadata);
            }
            return RiskRuleEvaluation.failed(id,
                    "open exposure exceeds the configured maximum", metadata);
        }
    }

    /**
     * Boundary: the leverage implied by the size stays inside the configured ceiling.
     */
    public record MaximumLeverage(String id) implements RiskRule {

        public MaximumLeverage {
            id = Rules.requireId(id);
        }

        @Override
        public RiskRuleEvaluation evaluate(RiskRuleContext context) {
            Rules.requireContext(context);
            if (!context.plan().sizingAvailable()) {
                return RiskRuleEvaluation.insufficient(id, "position size is unavailable");
            }
            BigDecimal leverage = context.plan().requiredLeverage();
            BigDecimal maximum = context.config().maximumLeverage();
            var metadata = RiskNumbers.metadata("requiredLeverage",
                    RiskNumbers.format(leverage),
                    "maximumLeverage", RiskNumbers.format(maximum));
            if (leverage.compareTo(maximum) <= 0) {
                return RiskRuleEvaluation.passed(id,
                        "required leverage is inside the configured ceiling", metadata);
            }
            return RiskRuleEvaluation.failed(id,
                    "required leverage exceeds the configured ceiling", metadata);
        }
    }

    /**
     * Boundary: the computed quantity stays inside the configured ceiling.
     */
    public record MaximumQuantity(String id) implements RiskRule {

        public MaximumQuantity {
            id = Rules.requireId(id);
        }

        @Override
        public RiskRuleEvaluation evaluate(RiskRuleContext context) {
            Rules.requireContext(context);
            if (!context.plan().sizingAvailable()) {
                return RiskRuleEvaluation.insufficient(id, "position size is unavailable");
            }
            BigDecimal quantity = context.plan().quantity();
            BigDecimal maximum = context.config().maximumQuantity();
            var metadata = RiskNumbers.metadata("quantity", RiskNumbers.format(quantity),
                    "maximumQuantity", RiskNumbers.format(maximum));
            if (quantity.compareTo(maximum) <= 0) {
                return RiskRuleEvaluation.passed(id,
                        "quantity is inside the configured maximum", metadata);
            }
            return RiskRuleEvaluation.failed(id,
                    "quantity exceeds the configured maximum", metadata);
        }
    }

    /**
     * Boundary: the option market's expected move covers the configured multiple of
     * the stop distance.
     *
     * <p>No target price is invented. When no option assessment exists the reward is
     * undetermined and the rule reports missing data.</p>
     */
    public record MinimumRewardRisk(String id) implements RiskRule {

        public MinimumRewardRisk {
            id = Rules.requireId(id);
        }

        @Override
        public RiskRuleEvaluation evaluate(RiskRuleContext context) {
            Rules.requireContext(context);
            if (!context.plan().stopAvailable()) {
                return RiskRuleEvaluation.insufficient(id, "stop distance is unavailable");
            }
            BigDecimal distance = context.plan().stopDistance();
            BigDecimal minimum = context.config().minimumRewardRiskRatio();
            BigDecimal required = distance.multiply(minimum);
            if (context.plan().availableReward().isEmpty()) {
                return RiskRuleEvaluation.insufficient(id,
                        "expected move is unavailable");
            }
            BigDecimal available = context.plan().availableReward().get();
            BigDecimal ratio = RiskNumbers.divide(available, distance);
            var metadata = RiskNumbers.metadata(
                    "availableReward", RiskNumbers.format(available),
                    "requiredReward", RiskNumbers.format(required),
                    "achievedRatio", RiskNumbers.format(ratio),
                    "minimumRatio", RiskNumbers.format(minimum));
            if (ratio.compareTo(minimum) >= 0) {
                return RiskRuleEvaluation.passed(id,
                        "expected move covers the required reward-to-risk ratio", metadata);
            }
            return RiskRuleEvaluation.failed(id,
                    "expected move is below the required reward-to-risk ratio", metadata);
        }
    }

    /**
     * Boundary: the stop clears a fraction of ATR so normal noise and gaps stay
     * outside it.
     */
    public record GapProtection(String id) implements RiskRule {

        public GapProtection {
            id = Rules.requireId(id);
        }

        @Override
        public RiskRuleEvaluation evaluate(RiskRuleContext context) {
            Rules.requireContext(context);
            if (!context.plan().stopAvailable() || context.plan().atr().isEmpty()) {
                return RiskRuleEvaluation.insufficient(id, "stop distance is unavailable");
            }
            BigDecimal floor = context.plan().atr().get().multiply(GAP_PROTECTION_ATR_FLOOR);
            BigDecimal distance = context.plan().stopDistance();
            var metadata = RiskNumbers.metadata("distance", RiskNumbers.format(distance),
                    "minimumGapDistance", RiskNumbers.format(floor));
            if (distance.compareTo(floor) >= 0) {
                return RiskRuleEvaluation.passed(id,
                        "stop distance clears the gap protection floor", metadata);
            }
            return RiskRuleEvaluation.failed(id,
                    "stop distance is inside the gap protection floor", metadata);
        }
    }

    /**
     * Boundary: for an option context, the stop-based loss stays inside the cash
     * risk implied by the expected move of the sized position.
     */
    public record OptionPremiumRisk(String id) implements RiskRule {

        public OptionPremiumRisk {
            id = Rules.requireId(id);
        }

        @Override
        public RiskRuleEvaluation evaluate(RiskRuleContext context) {
            Rules.requireContext(context);
            if (!context.context().hasOptions()) {
                return RiskRuleEvaluation.passed(id, "no option exposure",
                        RiskNumbers.metadata("options", "absent"));
            }
            if (!context.plan().sizingAvailable()) {
                return RiskRuleEvaluation.insufficient(id, "position size is unavailable");
            }
            if (context.plan().availableReward().isEmpty()) {
                return RiskRuleEvaluation.insufficient(id, "expected move is unavailable");
            }
            BigDecimal premiumRisk = context.plan().quantity()
                    .multiply(context.plan().availableReward().get());
            BigDecimal plannedRisk = context.plan().riskAmount();
            var metadata = RiskNumbers.metadata(
                    "plannedRisk", RiskNumbers.format(plannedRisk),
                    "optionRiskBudget", RiskNumbers.format(premiumRisk));
            if (plannedRisk.compareTo(premiumRisk) <= 0) {
                return RiskRuleEvaluation.passed(id,
                        "planned risk is inside the option risk budget", metadata);
            }
            return RiskRuleEvaluation.failed(id,
                    "planned risk exceeds the option risk budget", metadata);
        }
    }

    /**
     * Boundary: an instrument-specific risk percentage never exceeds the global
     * policy and is the one actually applied.
     */
    public record InstrumentOverride(String id) implements RiskRule {

        public InstrumentOverride {
            id = Rules.requireId(id);
        }

        @Override
        public RiskRuleEvaluation evaluate(RiskRuleContext context) {
            Rules.requireContext(context);
            var instrument = context.context().instrument();
            if (!context.config().hasOverride(instrument)) {
                return RiskRuleEvaluation.passed(id, "no instrument override applies",
                        RiskNumbers.metadata("override", "absent",
                                "riskPercent", RiskNumbers.format(
                                        context.plan().riskPercent())));
            }
            BigDecimal override = context.config().riskPercentFor(instrument);
            var metadata = RiskNumbers.metadata("override",
                    RiskNumbers.format(override),
                    "riskPercent", RiskNumbers.format(context.plan().riskPercent()));
            if (context.plan().riskPercent().compareTo(override) != 0) {
                return RiskRuleEvaluation.failed(id,
                        "applied risk percentage does not match the instrument override",
                        metadata);
            }
            return RiskRuleEvaluation.passed(id,
                    "instrument override was applied", metadata);
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
                throw new RiskValidationException("rule id must not be blank");
            }
            return value.trim();
        }

        static RiskRuleContext requireContext(RiskRuleContext context) {
            if (context == null) {
                throw new RiskValidationException("context must not be null");
            }
            return context;
        }
    }
}

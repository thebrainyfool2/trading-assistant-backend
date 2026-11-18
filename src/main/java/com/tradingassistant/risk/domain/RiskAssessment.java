package com.tradingassistant.risk.domain;

import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.qualification.domain.QualificationKey;
import com.tradingassistant.qualification.domain.QualificationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable description of the risk boundaries of one qualified trade.
 *
 * <p>The model answers "what are the allowable risk parameters under the
 * configured policies?", never "should this trade be taken?". It contains no
 * recommendation, no approval, no ranking, no order, no fill, no account balance
 * and no profit projection.</p>
 *
 * @param strategyId           strategy whose qualification was assessed
 * @param strategyName         strategy name
 * @param instrument           instrument of the assessment
 * @param interval             candle interval of the assessment
 * @param timestamp            market instant that was assessed
 * @param qualification        identity of the qualification this refers to
 * @param qualificationStatus  status of that qualification
 * @param status               aggregate outcome of the risk rules
 * @param riskPerTrade         risk budget committed by the policy
 * @param maximumPermittedLoss largest loss the boundaries allow
 * @param stop                 derived stop distance
 * @param atrMultiple          effective ATR multiple
 * @param volatilityAdjustment factor applied to the configured ATR multiple
 * @param sizing               derived position size
 * @param maximumLeverageAllowed leverage ceiling of the policy
 * @param riskReward           reward-to-risk comparison
 * @param dailyLoss            daily loss comparison
 * @param exposure             open exposure comparison
 * @param ruleEvaluations      individual rule outcomes in registry order
 * @param summary              short deterministic explanation
 * @param diagnostics          descriptive calculation inputs and intermediate values
 */
public record RiskAssessment(
        String strategyId,
        String strategyName,
        InstrumentKey instrument,
        Interval interval,
        Instant timestamp,
        QualificationKey qualification,
        QualificationStatus qualificationStatus,
        RiskStatus status,
        BigDecimal riskPerTrade,
        BigDecimal maximumPermittedLoss,
        StopCalculation stop,
        BigDecimal atrMultiple,
        BigDecimal volatilityAdjustment,
        PositionSizing sizing,
        BigDecimal maximumLeverageAllowed,
        RiskRewardValidation riskReward,
        DailyLossValidation dailyLoss,
        ExposureValidation exposure,
        List<RiskRuleEvaluation> ruleEvaluations,
        String summary,
        Map<String, String> diagnostics) {

    public RiskAssessment {
        if (strategyId == null || strategyId.isBlank()) {
            throw new IllegalArgumentException("strategyId must not be blank");
        }
        if (strategyName == null || strategyName.isBlank()) {
            throw new IllegalArgumentException("strategyName must not be blank");
        }
        instrument = Objects.requireNonNull(instrument, "instrument must not be null");
        interval = Objects.requireNonNull(interval, "interval must not be null");
        timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
        qualification = Objects.requireNonNull(qualification, "qualification must not be null");
        qualificationStatus = Objects.requireNonNull(qualificationStatus,
                "qualificationStatus must not be null");
        status = Objects.requireNonNull(status, "status must not be null");
        riskPerTrade = RiskValue.requireNonNegative(riskPerTrade, "riskPerTrade");
        maximumPermittedLoss = RiskValue.requireNonNegative(maximumPermittedLoss,
                "maximumPermittedLoss");
        stop = Objects.requireNonNull(stop, "stop must not be null");
        atrMultiple = RiskValue.requireNonNegative(atrMultiple, "atrMultiple");
        volatilityAdjustment = RiskValue.requireNonNegative(volatilityAdjustment,
                "volatilityAdjustment");
        sizing = Objects.requireNonNull(sizing, "sizing must not be null");
        maximumLeverageAllowed = RiskValue.requireNonNegative(maximumLeverageAllowed,
                "maximumLeverageAllowed");
        riskReward = Objects.requireNonNull(riskReward, "riskReward must not be null");
        dailyLoss = Objects.requireNonNull(dailyLoss, "dailyLoss must not be null");
        exposure = Objects.requireNonNull(exposure, "exposure must not be null");
        if (ruleEvaluations == null || ruleEvaluations.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("ruleEvaluations must not contain null entries");
        }
        ruleEvaluations = List.copyOf(ruleEvaluations);
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("summary must not be blank");
        }
        diagnostics = diagnostics == null ? Map.of() : Map.copyOf(diagnostics);
        strategyId = strategyId.trim();
        strategyName = strategyName.trim();
        summary = summary.trim();
    }

    /**
     * True only when every risk boundary held.
     */
    public boolean withinLimits() {
        return status == RiskStatus.WITHIN_LIMITS;
    }

    public BigDecimal quantity() {
        return sizing.quantity();
    }

    public BigDecimal positionValue() {
        return sizing.positionValue();
    }

    public BigDecimal stopDistance() {
        return stop.distance();
    }

    public int ruleCount() {
        return ruleEvaluations.size();
    }

    /**
     * Outcome recorded for one rule, when that rule took part.
     */
    public Optional<RiskRuleEvaluation> rule(String ruleId) {
        if (ruleId == null || ruleId.isBlank()) {
            return Optional.empty();
        }
        String trimmed = ruleId.trim();
        return ruleEvaluations.stream()
                .filter(evaluation -> evaluation.ruleId().equals(trimmed))
                .findFirst();
    }
}

package com.tradingassistant.risk.registry;

import com.tradingassistant.risk.api.RiskRule;
import com.tradingassistant.risk.rule.RiskRules;

import java.util.List;

/**
 * Deterministic catalog of the built-in risk rules.
 *
 * <p>Ordering is explicit so an event, a report and a test always see the same
 * sequence. The rules hold no state and read the policy from the rule context, so
 * the catalog does not depend on a configuration instance.</p>
 */
public final class BuiltInRiskRules {

    public static final String QUALIFICATION_ELIGIBLE = "QUALIFICATION_ELIGIBLE";
    public static final String ATR_STOP_VALIDATION = "ATR_STOP_VALIDATION";
    public static final String MINIMUM_STOP_DISTANCE = "MINIMUM_STOP_DISTANCE";
    public static final String MAXIMUM_STOP_DISTANCE = "MAXIMUM_STOP_DISTANCE";
    public static final String VOLATILITY_MULTIPLIER = "VOLATILITY_MULTIPLIER";
    public static final String MAXIMUM_RISK_PERCENT = "MAXIMUM_RISK_PERCENT";
    public static final String DAILY_RISK_LIMIT = "DAILY_RISK_LIMIT";
    public static final String SESSION_LOSS_CAP = "SESSION_LOSS_CAP";
    public static final String MAXIMUM_OPEN_EXPOSURE = "MAXIMUM_OPEN_EXPOSURE";
    public static final String MAXIMUM_LEVERAGE = "MAXIMUM_LEVERAGE";
    public static final String MAXIMUM_QUANTITY = "MAXIMUM_QUANTITY";
    public static final String MINIMUM_REWARD_RISK = "MINIMUM_REWARD_RISK";
    public static final String GAP_PROTECTION = "GAP_PROTECTION";
    public static final String OPTION_PREMIUM_RISK = "OPTION_PREMIUM_RISK";
    public static final String INSTRUMENT_OVERRIDE = "INSTRUMENT_OVERRIDE";

    private BuiltInRiskRules() {
    }

    /**
     * The default rule set, in evaluation order.
     */
    public static List<RiskRule> all() {
        return List.of(
                new RiskRules.QualificationEligible(QUALIFICATION_ELIGIBLE),
                new RiskRules.AtrStopValidation(ATR_STOP_VALIDATION),
                new RiskRules.MinimumStopDistance(MINIMUM_STOP_DISTANCE),
                new RiskRules.MaximumStopDistance(MAXIMUM_STOP_DISTANCE),
                new RiskRules.VolatilityMultiplier(VOLATILITY_MULTIPLIER),
                new RiskRules.MaximumRiskPercent(MAXIMUM_RISK_PERCENT),
                new RiskRules.DailyRiskLimit(DAILY_RISK_LIMIT),
                new RiskRules.SessionLossCap(SESSION_LOSS_CAP),
                new RiskRules.MaximumOpenExposure(MAXIMUM_OPEN_EXPOSURE),
                new RiskRules.MaximumLeverage(MAXIMUM_LEVERAGE),
                new RiskRules.MaximumQuantity(MAXIMUM_QUANTITY),
                new RiskRules.MinimumRewardRisk(MINIMUM_REWARD_RISK),
                new RiskRules.GapProtection(GAP_PROTECTION),
                new RiskRules.OptionPremiumRisk(OPTION_PREMIUM_RISK),
                new RiskRules.InstrumentOverride(INSTRUMENT_OVERRIDE));
    }
}

package com.tradingassistant.qualification.registry;

import com.tradingassistant.intelligence.domain.ParticipationState;
import com.tradingassistant.qualification.api.QualificationRule;
import com.tradingassistant.qualification.config.TradeQualificationConfig;
import com.tradingassistant.qualification.domain.QualificationLedger;
import com.tradingassistant.qualification.rule.QualificationRules;

import java.util.List;
import java.util.Objects;

/**
 * Deterministic catalog of the built-in qualification rules.
 *
 * <p>The catalog is assembled from configuration and the service's bounded ledger;
 * the rules themselves hold no state. Ordering is explicit so an event, a report
 * and a test always see the same sequence.</p>
 */
public final class BuiltInQualificationRules {

    public static final String SESSION_OPEN = "SESSION_OPEN";
    public static final String MARKET_ASSESSMENT_READY = "MARKET_ASSESSMENT_READY";
    public static final String OPTIONS_REQUIRED = "OPTIONS_REQUIRED";
    public static final String OPTIONS_FRESH = "OPTIONS_FRESH";
    public static final String SESSION_ALIGNED = "SESSION_ALIGNED";
    public static final String STRATEGY_STATUS_KNOWN = "STRATEGY_STATUS_KNOWN";
    public static final String STRATEGY_STATUS_PASSED = "STRATEGY_STATUS_PASSED";
    public static final String MARKET_CONFIDENCE = "MARKET_CONFIDENCE";
    public static final String MARKET_PARTICIPATION = "MARKET_PARTICIPATION";
    public static final String OPTION_LIQUIDITY = "OPTION_LIQUIDITY";
    public static final String DUPLICATE_QUALIFICATION = "DUPLICATE_QUALIFICATION";

    /** Participation floor: a weak move is not qualified. */
    public static final ParticipationState MINIMUM_PARTICIPATION = ParticipationState.AVERAGE;

    private BuiltInQualificationRules() {
    }

    /**
     * Builds the default rule set for a configuration and a qualification ledger.
     */
    public static List<QualificationRule> all(TradeQualificationConfig config,
                                              QualificationLedger ledger) {
        Objects.requireNonNull(config, "config must not be null");
        Objects.requireNonNull(ledger, "ledger must not be null");
        return List.of(
                new QualificationRules.SessionOpen(SESSION_OPEN),
                new QualificationRules.MarketAssessmentReady(MARKET_ASSESSMENT_READY),
                new QualificationRules.OptionsRequirement(OPTIONS_REQUIRED, config.requireOptions()),
                new QualificationRules.OptionsFreshness(OPTIONS_FRESH, config.requireOptions()),
                new QualificationRules.SessionAlignment(SESSION_ALIGNED, config.requireOptions()),
                new QualificationRules.StrategyStatusKnown(STRATEGY_STATUS_KNOWN),
                new QualificationRules.StrategyStatusPassed(STRATEGY_STATUS_PASSED),
                new QualificationRules.MinimumMarketConfidence(MARKET_CONFIDENCE,
                        config.minimumConfidence()),
                new QualificationRules.MinimumParticipation(MARKET_PARTICIPATION,
                        MINIMUM_PARTICIPATION),
                new QualificationRules.OptionLiquidity(OPTION_LIQUIDITY, config.requireOptions()),
                new QualificationRules.DuplicateQualification(DUPLICATE_QUALIFICATION, ledger));
    }
}

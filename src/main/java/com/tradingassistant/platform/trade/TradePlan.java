package com.tradingassistant.platform.trade;

import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.OptionType;
import com.tradingassistant.recommendation.domain.RecommendationType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;

/**
 * One deterministic, read-only reference plan for an option trade.
 *
 * <p>This is not an order and not a promise. The owner still decides and
 * executes manually. Premium boundaries are a linear approximation from the
 * underlying ATR stop and the ATM Greek, not a forecast.</p>
 */
public record TradePlan(
        RecommendationType direction,
        InstrumentKey underlying,
        InstrumentKey optionInstrument,
        String optionSymbol,
        LocalDate expiry,
        BigDecimal strike,
        OptionType optionType,
        BigDecimal lastPremium,
        BigDecimal suggestedEntry,
        BigDecimal targetPremium,
        BigDecimal stopPremium,
        BigDecimal premiumTargetDistance,
        BigDecimal premiumStopDistance,
        BigDecimal underlyingTargetDistance,
        BigDecimal underlyingStopDistance,
        BigDecimal delta,
        long lotSize,
        BigDecimal suggestedQuantity,
        BigDecimal maximumLoss,
        BigDecimal potentialReward,
        BigDecimal rewardRiskRatio,
        String basis,
        Instant generatedAt,
        Map<String, String> diagnostics) {

    public TradePlan {
        direction = Objects.requireNonNull(direction, "direction must not be null");
        underlying = Objects.requireNonNull(underlying, "underlying must not be null");
        optionInstrument = Objects.requireNonNull(optionInstrument,
                "optionInstrument must not be null");
        optionSymbol = Objects.requireNonNull(optionSymbol, "optionSymbol must not be null");
        expiry = Objects.requireNonNull(expiry, "expiry must not be null");
        strike = Objects.requireNonNull(strike, "strike must not be null");
        optionType = Objects.requireNonNull(optionType, "optionType must not be null");
        lastPremium = positive(lastPremium, "lastPremium");
        suggestedEntry = positive(suggestedEntry, "suggestedEntry");
        targetPremium = positive(targetPremium, "targetPremium");
        stopPremium = positive(stopPremium, "stopPremium");
        if (targetPremium.compareTo(suggestedEntry) <= 0) {
            throw new IllegalArgumentException("targetPremium must be above the entry");
        }
        if (stopPremium.compareTo(suggestedEntry) >= 0) {
            throw new IllegalArgumentException("stopPremium must be below the entry");
        }
        premiumTargetDistance = nonNegative(premiumTargetDistance,
                "premiumTargetDistance");
        premiumStopDistance = nonNegative(premiumStopDistance, "premiumStopDistance");
        underlyingTargetDistance = nonNegative(underlyingTargetDistance,
                "underlyingTargetDistance");
        underlyingStopDistance = nonNegative(underlyingStopDistance,
                "underlyingStopDistance");
        delta = nonNegative(delta, "delta");
        if (lotSize <= 0) {
            throw new IllegalArgumentException("lotSize must be positive");
        }
        suggestedQuantity = nonNegative(suggestedQuantity, "suggestedQuantity");
        maximumLoss = nonNegative(maximumLoss, "maximumLoss");
        potentialReward = nonNegative(potentialReward, "potentialReward");
        rewardRiskRatio = nonNegative(rewardRiskRatio, "rewardRiskRatio");
        basis = requireText(basis, "basis");
        generatedAt = Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        diagnostics = diagnostics == null ? Map.of() : Map.copyOf(diagnostics);
    }

    private static BigDecimal positive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    private static BigDecimal nonNegative(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
        return value;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}

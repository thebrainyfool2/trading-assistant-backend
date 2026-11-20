package com.tradingassistant.monitoring.domain;

import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.recommendation.domain.RecommendationType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * A trade the owner has already executed outside the application and recorded
 * manually for monitoring.
 *
 * <p>This model deliberately has no broker order id, execution state, fill
 * status, stop instruction or automation flag. Recording a trade never places,
 * modifies or closes an order.</p>
 */
public record ManualTrade(
        String tradeId,
        InstrumentKey instrument,
        Interval interval,
        RecommendationType recommendationType,
        String optionSymbol,
        BigDecimal strike,
        LocalDate expiry,
        Instant entryTimestamp,
        BigDecimal entryPrice,
        BigDecimal quantity,
        String notes,
        ManualTradeStatus status,
        Optional<BigDecimal> targetPremium,
        Optional<BigDecimal> stopPremium,
        Optional<Instant> recommendationTimestamp) {

    public ManualTrade(String tradeId, InstrumentKey instrument, Interval interval,
                       RecommendationType recommendationType, String optionSymbol,
                       BigDecimal strike, LocalDate expiry, Instant entryTimestamp,
                       BigDecimal entryPrice, BigDecimal quantity, String notes,
                       ManualTradeStatus status) {
        this(tradeId, instrument, interval, recommendationType, optionSymbol, strike,
                expiry, entryTimestamp, entryPrice, quantity, notes, status,
                Optional.empty(), Optional.empty(), Optional.empty());
    }

    public ManualTrade(String tradeId, InstrumentKey instrument, Interval interval,
                       RecommendationType recommendationType, String optionSymbol,
                       BigDecimal strike, LocalDate expiry, Instant entryTimestamp,
                       BigDecimal entryPrice, BigDecimal quantity, String notes,
                       ManualTradeStatus status, Optional<BigDecimal> targetPremium,
                       Optional<BigDecimal> stopPremium) {
        this(tradeId, instrument, interval, recommendationType, optionSymbol, strike,
                expiry, entryTimestamp, entryPrice, quantity, notes, status,
                targetPremium, stopPremium, Optional.empty());
    }

    public ManualTrade {
        if (tradeId == null || tradeId.isBlank()) {
            throw new IllegalArgumentException("tradeId must not be blank");
        }
        tradeId = tradeId.trim();
        if (tradeId.length() > 80) {
            throw new IllegalArgumentException("tradeId must not exceed 80 characters");
        }
        instrument = Objects.requireNonNull(instrument, "instrument must not be null");
        interval = Objects.requireNonNull(interval, "interval must not be null");
        recommendationType = Objects.requireNonNull(recommendationType,
                "recommendationType must not be null");
        if (recommendationType != RecommendationType.BUY_CALL
                && recommendationType != RecommendationType.BUY_PUT) {
            throw new IllegalArgumentException(
                    "a manual trade must be BUY_CALL or BUY_PUT");
        }
        if (optionSymbol == null || optionSymbol.isBlank()) {
            throw new IllegalArgumentException("optionSymbol must not be blank");
        }
        optionSymbol = optionSymbol.trim().toUpperCase(java.util.Locale.ROOT);
        strike = Objects.requireNonNull(strike, "strike must not be null");
        if (strike.signum() <= 0) {
            throw new IllegalArgumentException("strike must be positive");
        }
        expiry = Objects.requireNonNull(expiry, "expiry must not be null");
        entryTimestamp = Objects.requireNonNull(entryTimestamp,
                "entryTimestamp must not be null");
        entryPrice = Objects.requireNonNull(entryPrice, "entryPrice must not be null");
        if (entryPrice.signum() <= 0) {
            throw new IllegalArgumentException("entryPrice must be positive");
        }
        quantity = Objects.requireNonNull(quantity, "quantity must not be null");
        if (quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        notes = notes == null ? "" : notes.trim();
        status = Objects.requireNonNull(status, "status must not be null");
        BigDecimal recordedEntry = entryPrice;
        Optional<BigDecimal> checkedTarget = targetPremium == null
                ? Optional.empty() : targetPremium;
        Optional<BigDecimal> checkedStop = stopPremium == null
                ? Optional.empty() : stopPremium;
        checkedTarget.ifPresent(value -> {
            if (value.signum() <= 0) {
                throw new IllegalArgumentException("targetPremium must be positive");
            }
            if (value.compareTo(recordedEntry) <= 0) {
                throw new IllegalArgumentException("targetPremium must be above entryPrice");
            }
        });
        checkedStop.ifPresent(value -> {
            if (value.signum() <= 0) {
                throw new IllegalArgumentException("stopPremium must be positive");
            }
            if (value.compareTo(recordedEntry) >= 0) {
                throw new IllegalArgumentException("stopPremium must be below entryPrice");
            }
        });
        targetPremium = checkedTarget;
        stopPremium = checkedStop;
        recommendationTimestamp = recommendationTimestamp == null
                ? Optional.empty() : recommendationTimestamp;
    }

    public boolean active() {
        return status == ManualTradeStatus.ACTIVE;
    }

    public boolean matches(InstrumentKey candidate, Interval candidateInterval) {
        return instrument.equals(candidate) && interval == candidateInterval;
    }

    /**
     * Records that the owner has exited the trade. This changes local monitoring
     * state only; it never contacts a broker.
     */
    public ManualTrade closed() {
        return new ManualTrade(tradeId, instrument, interval, recommendationType,
                optionSymbol, strike, expiry, entryTimestamp, entryPrice, quantity,
                notes, ManualTradeStatus.CLOSED, targetPremium, stopPremium,
                recommendationTimestamp);
    }
}
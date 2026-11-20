package com.tradingassistant.platform.trade;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** One live premium observation for a manually recorded option trade. */
public record OptionPremiumSnapshot(
        String tradeId,
        String optionInstrument,
        String optionSymbol,
        BigDecimal entryPremium,
        Optional<BigDecimal> premium,
        Optional<BigDecimal> profitLoss,
        Optional<BigDecimal> profitLossPercent,
        Optional<BigDecimal> targetPremium,
        Optional<BigDecimal> stopPremium,
        PremiumStatus status,
        boolean sessionOpen,
        Instant timestamp) {

    public OptionPremiumSnapshot {
        if (tradeId == null || tradeId.isBlank()) {
            throw new IllegalArgumentException("tradeId must not be blank");
        }
        optionInstrument = optionInstrument == null ? "" : optionInstrument;
        optionSymbol = optionSymbol == null ? "" : optionSymbol;
        entryPremium = Objects.requireNonNull(entryPremium, "entryPremium must not be null");
        premium = premium == null ? Optional.empty() : premium;
        profitLoss = profitLoss == null ? Optional.empty() : profitLoss;
        profitLossPercent = profitLossPercent == null
                ? Optional.empty() : profitLossPercent;
        targetPremium = targetPremium == null ? Optional.empty() : targetPremium;
        stopPremium = stopPremium == null ? Optional.empty() : stopPremium;
        status = Objects.requireNonNull(status, "status must not be null");
        timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
    }
}

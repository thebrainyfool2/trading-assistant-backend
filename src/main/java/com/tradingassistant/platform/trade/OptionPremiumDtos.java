package com.tradingassistant.platform.trade;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/** Read-only option-premium status for the owner. */
public final class OptionPremiumDtos {

    private OptionPremiumDtos() {
    }

    public record PremiumView(
            String tradeId,
            String optionInstrument,
            String optionSymbol,
            BigDecimal entryPremium,
            Optional<BigDecimal> premium,
            Optional<BigDecimal> profitLoss,
            Optional<BigDecimal> profitLossPercent,
            Optional<BigDecimal> targetPremium,
            Optional<BigDecimal> stopPremium,
            String status,
            boolean sessionOpen,
            Instant timestamp) {

        public static PremiumView from(OptionPremiumSnapshot snapshot) {
            return new PremiumView(snapshot.tradeId(), snapshot.optionInstrument(),
                    snapshot.optionSymbol(), snapshot.entryPremium(),
                    snapshot.premium(), snapshot.profitLoss(),
                    snapshot.profitLossPercent(), snapshot.targetPremium(),
                    snapshot.stopPremium(), snapshot.status().name(),
                    snapshot.sessionOpen(), snapshot.timestamp());
        }
    }
}

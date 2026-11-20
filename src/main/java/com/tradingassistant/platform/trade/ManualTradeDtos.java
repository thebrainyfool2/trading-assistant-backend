package com.tradingassistant.platform.trade;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Write contracts for the owner's manually executed option trades. */
public final class ManualTradeDtos {

    private ManualTradeDtos() {
    }

    public record CreateTradeRequest(
            String tradeId,
            @NotBlank String instrument,
            @NotBlank String recommendationType,
            String interval,
            @NotNull Instant recommendationTimestamp,
            @NotBlank String optionSymbol,
            @NotNull BigDecimal strike,
            @NotNull LocalDate expiry,
            Instant entryTimestamp,
            @NotNull @Positive BigDecimal entryPrice,
            @NotNull @Positive BigDecimal quantity,
            String notes,
            BigDecimal targetPremium,
            BigDecimal stopPremium) {
    }

    public record CloseTradeRequest(String reason) {
    }
}

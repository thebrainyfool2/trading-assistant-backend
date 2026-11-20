package com.tradingassistant.monitoring.persistence;

import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.monitoring.domain.ManualTrade;
import com.tradingassistant.monitoring.domain.ManualTradeStatus;
import com.tradingassistant.recommendation.domain.RecommendationType;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/** Mongo representation of a manually recorded, non-executed trade. */
@Document(collection = "manual_trades")
public record ManualTradeDocument(
        @Id String tradeId,
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
        @Indexed ManualTradeStatus status,
        BigDecimal targetPremium,
        BigDecimal stopPremium,
        Instant recommendationTimestamp) {

    public static ManualTradeDocument from(ManualTrade trade) {
        return new ManualTradeDocument(trade.tradeId(), trade.instrument(),
                trade.interval(), trade.recommendationType(), trade.optionSymbol(),
                trade.strike(), trade.expiry(), trade.entryTimestamp(),
                trade.entryPrice(), trade.quantity(), trade.notes(), trade.status(),
                trade.targetPremium().orElse(null), trade.stopPremium().orElse(null),
                trade.recommendationTimestamp().orElse(null));
    }

    public ManualTrade toDomain() {
        return new ManualTrade(tradeId, instrument, interval, recommendationType,
                optionSymbol, strike, expiry, entryTimestamp, entryPrice, quantity,
                notes, status, Optional.ofNullable(targetPremium),
                Optional.ofNullable(stopPremium),
                Optional.ofNullable(recommendationTimestamp));
    }
}
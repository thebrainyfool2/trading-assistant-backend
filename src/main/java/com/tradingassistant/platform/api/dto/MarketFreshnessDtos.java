package com.tradingassistant.platform.api.dto;

import com.tradingassistant.platform.readmodel.MarketDataFreshnessService;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * API projection of the freshness projection. It intentionally exposes policy
 * outcomes, not thresholds or internal configuration.
 */
public final class MarketFreshnessDtos {

    private MarketFreshnessDtos() {
    }

    public record FreshnessView(
            String segment,
            String symbolToken,
            String status,
            String session,
            Instant lastTickAt,
            Instant lastCandleAt,
            String candleInterval,
            Long tickAgeSeconds,
            Long candleAgeSeconds,
            BigDecimal latestPrice) {

        public static FreshnessView from(MarketDataFreshnessService.Freshness freshness) {
            return new FreshnessView(
                    freshness.instrument().segment().name(),
                    freshness.instrument().symbolToken(),
                    freshness.status().name(),
                    freshness.session().name(),
                    freshness.lastTickAt(),
                    freshness.lastCandleAt(),
                    freshness.candleInterval() == null
                            ? null : freshness.candleInterval().name(),
                    freshness.tickAgeSeconds(),
                    freshness.candleAgeSeconds(),
                    freshness.latestPrice());
        }
    }
}

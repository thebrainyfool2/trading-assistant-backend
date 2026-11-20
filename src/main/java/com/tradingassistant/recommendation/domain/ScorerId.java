package com.tradingassistant.recommendation.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * Stable identities of the independent recommendation scorers.
 *
 * <p>The list is explicit so a configuration that omits or duplicates a scorer
 * can be rejected before any recommendation is produced.</p>
 */
public enum ScorerId {

    TREND("trend", "Trend"),
    MOMENTUM("momentum", "Momentum"),
    VOLATILITY("volatility", "Volatility"),
    LIQUIDITY("liquidity", "Liquidity"),
    VOLUME("volume", "Volume"),
    OPEN_INTEREST("open_interest", "Open interest"),
    OI_CHANGE("oi_change", "OI change"),
    PCR("pcr", "Put/call ratio"),
    IV("iv", "Implied volatility"),
    IV_RANK("iv_rank", "IV rank"),
    GREEKS("greeks", "Greeks"),
    EXPECTED_MOVE("expected_move", "Expected move"),
    SUPPORT_RESISTANCE("support_resistance", "Support/resistance"),
    BREAKOUT("breakout", "Breakout"),
    MARKET_STRUCTURE("market_structure", "Market structure"),
    TREND_ALIGNMENT("trend_alignment", "Trend alignment"),
    RISK_REWARD("risk_reward", "Risk/reward"),
    QUALIFICATION("qualification", "Qualification"),
    STRATEGY_AGREEMENT("strategy_agreement", "Strategy agreement"),
    MARKET_SESSION("market_session", "Market session"),
    FRESHNESS("freshness", "Freshness");

    private final String id;
    private final String label;

    ScorerId(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public static Optional<ScorerId> fromId(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT)
                .replace('-', '_');
        for (ScorerId id : values()) {
            if (id.id.equals(normalized)) {
                return Optional.of(id);
            }
        }
        return Optional.empty();
    }
}

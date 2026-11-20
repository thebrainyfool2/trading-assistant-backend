package com.tradingassistant.monitoring.domain;

import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.monitoring.MonitoringTestFixtures;
import com.tradingassistant.recommendation.domain.RecommendationType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManualTradeTest {

    @Test
    void recordsOnlyManualCallOrPutTradesAndClosesLocally() {
        ManualTrade trade = MonitoringTestFixtures.callTrade();

        assertThat(trade.active()).isTrue();
        assertThat(trade.matches(MonitoringTestFixtures.SPOT, Interval.ONE_MINUTE))
                .isTrue();
        ManualTrade closed = trade.closed();
        assertThat(closed.active()).isFalse();
        assertThat(closed.status()).isEqualTo(ManualTradeStatus.CLOSED);
        assertThat(closed.tradeId()).isEqualTo(trade.tradeId());
    }

    @Test
    void rejectsNoTradeDirectionAndNonPositiveEconomics() {
        assertThatThrownBy(() -> trade(RecommendationType.NO_TRADE, new BigDecimal("10"),
                new BigDecimal("50")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BUY_CALL or BUY_PUT");
        assertThatThrownBy(() -> trade(RecommendationType.BUY_CALL, BigDecimal.ZERO,
                new BigDecimal("50")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entryPrice");
        assertThatThrownBy(() -> trade(RecommendationType.BUY_CALL, new BigDecimal("10"),
                BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity");
    }

    private static ManualTrade trade(RecommendationType type, BigDecimal entryPrice,
                                     BigDecimal quantity) {
        return new ManualTrade("test-trade", MonitoringTestFixtures.SPOT,
                MonitoringTestFixtures.INTERVAL, type, "NIFTY-CE-500",
                new BigDecimal("500"),
                com.tradingassistant.options.OptionsTestFixtures.EXPIRY,
                MonitoringTestFixtures.START, entryPrice, quantity, "",
                ManualTradeStatus.ACTIVE);
    }
}
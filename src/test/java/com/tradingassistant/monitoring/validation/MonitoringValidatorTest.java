package com.tradingassistant.monitoring.validation;

import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.monitoring.MonitoringTestFixtures;
import com.tradingassistant.monitoring.domain.ManualTrade;
import com.tradingassistant.monitoring.domain.ManualTradeStatus;
import com.tradingassistant.monitoring.domain.TradeMonitoringContext;
import com.tradingassistant.monitoring.domain.TradeMonitoringResult;
import com.tradingassistant.monitoring.engine.TradeMonitoringEngine;
import com.tradingassistant.monitoring.exception.MonitoringValidationException;
import com.tradingassistant.monitoring.validation.MonitoringValidator;
import com.tradingassistant.recommendation.domain.RecommendationType;
import com.tradingassistant.recommendation.domain.Recommendation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MonitoringValidatorTest {

    private final MonitoringValidator validator = new MonitoringValidator(
            MonitoringTestFixtures.config());

    @Test
    void acceptsAlignedActiveContext() {
        TradeMonitoringContext context = MonitoringTestFixtures.context(
                MonitoringTestFixtures.callTrade(),
                MonitoringTestFixtures.defaultCall());

        assertThat(validator.align(context)).isSameAs(context);
    }

    @Test
    void rejectsClosedTrades() {
        ManualTrade closed = MonitoringTestFixtures.callTrade().closed();

        assertThatThrownBy(() -> validator.align(MonitoringTestFixtures.context(closed,
                MonitoringTestFixtures.defaultCall())))
                .isInstanceOf(MonitoringValidationException.class)
                .hasMessageContaining("closed");
    }

    @Test
    void rejectsMissingPreviousContextAndMissingRecommendation() {
        assertThatThrownBy(() -> new TradeMonitoringContext(
                MonitoringTestFixtures.callTrade(), null,
                com.tradingassistant.recommendation.RecommendationTestFixtures.bullishMarket(),
                java.util.Map.of(),
                com.tradingassistant.recommendation.RecommendationTestFixtures.bullishOptions(),
                com.tradingassistant.recommendation.RecommendationTestFixtures.strategy(),
                com.tradingassistant.recommendation.RecommendationTestFixtures.qualification(),
                com.tradingassistant.recommendation.RecommendationTestFixtures.risk(),
                MonitoringTestFixtures.freshness(), Optional.empty()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsStaleDataAndAgedDataBeyondTheConfiguredDuration() {
        TradeMonitoringContext stale = MonitoringTestFixtures.context(
                MonitoringTestFixtures.callTrade(),
                MonitoringTestFixtures.defaultCall(),
                com.tradingassistant.recommendation.RecommendationTestFixtures.bullishMarket(),
                com.tradingassistant.recommendation.RecommendationTestFixtures.bullishOptions(),
                com.tradingassistant.recommendation.RecommendationTestFixtures.qualification(),
                com.tradingassistant.recommendation.RecommendationTestFixtures.risk(),
                MonitoringTestFixtures.staleFreshness(), Optional.empty());
        TradeMonitoringContext aged = MonitoringTestFixtures.context(
                MonitoringTestFixtures.callTrade(),
                MonitoringTestFixtures.defaultCall(),
                com.tradingassistant.recommendation.RecommendationTestFixtures.bullishMarket(),
                com.tradingassistant.recommendation.RecommendationTestFixtures.bullishOptions(),
                com.tradingassistant.recommendation.RecommendationTestFixtures.qualification(),
                com.tradingassistant.recommendation.RecommendationTestFixtures.risk(),
                MonitoringTestFixtures.agedFreshness(), Optional.empty());

        assertThatThrownBy(() -> validator.align(stale))
                .isInstanceOf(MonitoringValidationException.class)
                .hasMessageContaining("not fresh");
        assertThatThrownBy(() -> validator.align(aged))
                .isInstanceOf(MonitoringValidationException.class)
                .hasMessageContaining("maximum stale duration");
    }

    @Test
    void rejectsMismatchedInstrument() {
        ManualTrade other = new ManualTrade("trade-2",
                new InstrumentKey(com.tradingassistant.market.domain.Segment.NSE_CASH, "9999"),
                Interval.ONE_MINUTE, RecommendationType.BUY_CALL, "OTHER-CE",
                new BigDecimal("500"), com.tradingassistant.options.OptionsTestFixtures.EXPIRY,
                MonitoringTestFixtures.START.minusSeconds(60), new BigDecimal("10"),
                new BigDecimal("50"), "", ManualTradeStatus.ACTIVE);

        assertThatThrownBy(() -> validator.align(MonitoringTestFixtures.context(other,
                MonitoringTestFixtures.defaultCall())))
                .isInstanceOf(MonitoringValidationException.class)
                .hasMessageContaining("trade instrument");
    }

    @Test
    void rejectsMismatchedExpiry() {
        ManualTrade otherExpiry = new ManualTrade(MonitoringTestFixtures.TRADE_ID,
                MonitoringTestFixtures.SPOT, MonitoringTestFixtures.INTERVAL,
                RecommendationType.BUY_CALL, "NIFTY-CE-500", new BigDecimal("500"),
                com.tradingassistant.options.OptionsTestFixtures.EXPIRY.plusDays(7),
                MonitoringTestFixtures.START.minusSeconds(60), new BigDecimal("10"),
                new BigDecimal("50"), "", ManualTradeStatus.ACTIVE);

        assertThatThrownBy(() -> validator.align(MonitoringTestFixtures.context(otherExpiry,
                MonitoringTestFixtures.defaultCall())))
                .isInstanceOf(MonitoringValidationException.class)
                .hasMessageContaining("expiry");
    }

    @Test
    void rejectsMismatchedRecommendationTimestamp() {
        Recommendation original = MonitoringTestFixtures.defaultCall();
        Recommendation shifted = new Recommendation(original.type(),
                original.instrument(), original.interval(),
                MonitoringTestFixtures.START.plusSeconds(1), original.weightedScore(),
                original.confidence(), original.components(), original.option(),
                original.summary(), original.diagnostics());

        assertThatThrownBy(() -> validator.align(MonitoringTestFixtures.context(
                MonitoringTestFixtures.callTrade(), shifted)))
                .isInstanceOf(MonitoringValidationException.class)
                .hasMessageContaining("timestamp");
    }

    @Test
    void rejectsMismatchedPreviousTrade() {
        TradeMonitoringEngine engine = new TradeMonitoringEngine(
                MonitoringTestFixtures.config());
        TradeMonitoringResult first = engine.evaluate(MonitoringTestFixtures.context(
                MonitoringTestFixtures.callTrade(), MonitoringTestFixtures.defaultCall()));
        TradeMonitoringResult wrongTrade = new TradeMonitoringResult("other-trade",
                first.timestamp(), first.monitoringState(), first.exitRecommendation(),
                first.currentConfidence(), first.currentDirectionalEdge(),
                first.confidenceDelta(), first.directionalEdgeDelta(),
                first.componentChanges(), first.componentScores(), first.warnings(),
                first.explanation(), first.diagnostics());

        assertThatThrownBy(() -> validator.align(MonitoringTestFixtures.context(
                MonitoringTestFixtures.callTrade(), MonitoringTestFixtures.defaultCall(),
                com.tradingassistant.recommendation.RecommendationTestFixtures.bullishMarket(),
                com.tradingassistant.recommendation.RecommendationTestFixtures.bullishOptions(),
                com.tradingassistant.recommendation.RecommendationTestFixtures.qualification(),
                com.tradingassistant.recommendation.RecommendationTestFixtures.risk(),
                MonitoringTestFixtures.freshness(), Optional.of(wrongTrade))))
                .isInstanceOf(MonitoringValidationException.class)
                .hasMessageContaining("different trade");
    }
}
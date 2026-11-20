package com.tradingassistant.monitoring;

import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.OptionType;
import com.tradingassistant.monitoring.config.MonitoringConfig;
import com.tradingassistant.monitoring.domain.ManualTrade;
import com.tradingassistant.monitoring.domain.ManualTradeStatus;
import com.tradingassistant.monitoring.domain.MonitoringInterval;
import com.tradingassistant.monitoring.domain.TradeMonitoringContext;
import com.tradingassistant.monitoring.domain.TradeMonitoringResult;
import com.tradingassistant.options.OptionsTestFixtures;
import com.tradingassistant.options.domain.ExpiryKind;
import com.tradingassistant.options.domain.Moneyness;
import com.tradingassistant.options.domain.OptionsAssessment;
import com.tradingassistant.platform.readmodel.MarketDataFreshnessService.Freshness;
import com.tradingassistant.platform.readmodel.MarketDataFreshnessService.FreshnessStatus;
import com.tradingassistant.platform.readmodel.MarketDataFreshnessService.MarketSessionState;
import com.tradingassistant.qualification.domain.TradeQualification;
import com.tradingassistant.recommendation.RecommendationTestFixtures;
import com.tradingassistant.recommendation.config.StrikeMode;
import com.tradingassistant.recommendation.domain.ComponentScore;
import com.tradingassistant.recommendation.domain.DirectionalBias;
import com.tradingassistant.recommendation.domain.OptionSelection;
import com.tradingassistant.recommendation.domain.Recommendation;
import com.tradingassistant.recommendation.domain.RecommendationType;
import com.tradingassistant.recommendation.domain.ScorerId;
import com.tradingassistant.risk.domain.RiskAssessment;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Deterministic monitoring fixtures built from immutable existing-engine models. */
public final class MonitoringTestFixtures {

    public static final InstrumentKey SPOT = RecommendationTestFixtures.SPOT;
    public static final Interval INTERVAL = RecommendationTestFixtures.INTERVAL;
    public static final Instant START = RecommendationTestFixtures.START;
    public static final String TRADE_ID = "trade-1";

    private MonitoringTestFixtures() {
    }

    public static MonitoringConfig config() {
        return new MonitoringConfig(true, new BigDecimal("65"), new BigDecimal("45"),
                new BigDecimal("10"), new BigDecimal("8"), new BigDecimal("10"),
                1, 3, new BigDecimal("50"), Duration.ofMinutes(2),
                MonitoringInterval.EVENT_DRIVEN);
    }

    public static MonitoringConfig config(boolean enabled) {
        MonitoringConfig base = config();
        return new MonitoringConfig(enabled, base.warningConfidence(),
                base.exitConfidence(), base.confidenceDeterioration(),
                base.edgeDeterioration(), base.componentDeterioration(),
                base.warningComponentCount(), base.atRiskComponentCount(),
                base.strategyDisagreementScore(), base.maximumStaleDuration(),
                base.interval());
    }

    public static ManualTrade callTrade() {
        return trade(RecommendationType.BUY_CALL);
    }

    public static ManualTrade putTrade() {
        return trade(RecommendationType.BUY_PUT);
    }

    public static ManualTrade trade(RecommendationType type) {
        return new ManualTrade(TRADE_ID, SPOT, INTERVAL, type,
                type == RecommendationType.BUY_CALL ? "NIFTY-CE-500" : "NIFTY-PE-500",
                new BigDecimal("500"), OptionsTestFixtures.EXPIRY,
                START.minusSeconds(60), new BigDecimal("12.50"),
                new BigDecimal("50"), "manually executed", ManualTradeStatus.ACTIVE);
    }

    public static Recommendation recommendation(RecommendationType type,
                                                String confidence) {
        return recommendation(type, new BigDecimal(confidence),
                Map.of(ScorerId.TREND, new BigDecimal("70"),
                        ScorerId.MOMENTUM, new BigDecimal("70"),
                        ScorerId.LIQUIDITY, new BigDecimal("70")),
                new BigDecimal("30"));
    }

    public static Recommendation recommendation(RecommendationType type,
                                                BigDecimal confidence,
                                                Map<ScorerId, BigDecimal> scores,
                                                BigDecimal edgeSupport) {
        List<ComponentScore> components = new ArrayList<>();
        for (Map.Entry<ScorerId, BigDecimal> entry : scores.entrySet()) {
            boolean directional = entry.getKey() == ScorerId.TREND;
            DirectionalBias bias = directional
                    ? (type == RecommendationType.BUY_PUT
                    ? DirectionalBias.BEARISH : DirectionalBias.BULLISH)
                    : DirectionalBias.NEUTRAL;
            BigDecimal contribution = directional ? edgeSupport : BigDecimal.ZERO;
            components.add(new ComponentScore(entry.getKey(), entry.getKey().label(),
                    entry.getValue(), BigDecimal.valueOf(100), BigDecimal.TEN,
                    contribution, bias, entry.getKey().label() + " fixture", Map.of()));
        }
        Optional<OptionSelection> selection = type == RecommendationType.NO_TRADE
                ? Optional.empty()
                : Optional.of(new OptionSelection(OptionsTestFixtures.EXPIRY,
                ExpiryKind.WEEKLY, new BigDecimal("500"),
                type == RecommendationType.BUY_CALL ? OptionType.CALL : OptionType.PUT,
                Moneyness.ATM, StrikeMode.ATM, 0));
        return new Recommendation(type, SPOT, INTERVAL, START,
                new BigDecimal("100"), confidence, components, selection,
                "fixture recommendation", Map.of("decisionReason", "fixture"));
    }

    public static Recommendation recommendation(RecommendationType type,
                                                String confidence,
                                                Map<ScorerId, BigDecimal> scores,
                                                BigDecimal edgeSupport) {
        return recommendation(type, new BigDecimal(confidence), scores, edgeSupport);
    }

    public static Recommendation defaultCall() {
        return recommendation(RecommendationType.BUY_CALL, "72");
    }

    public static Recommendation weakCall() {
        return recommendation(RecommendationType.BUY_CALL, "58",
                Map.of(ScorerId.TREND, new BigDecimal("50"),
                        ScorerId.MOMENTUM, new BigDecimal("50"),
                        ScorerId.LIQUIDITY, new BigDecimal("50")),
                new BigDecimal("15"));
    }

    public static Recommendation noTrade() {
        return recommendation(RecommendationType.NO_TRADE, "72",
                Map.of(ScorerId.TREND, new BigDecimal("40"),
                        ScorerId.MOMENTUM, new BigDecimal("40"),
                        ScorerId.LIQUIDITY, new BigDecimal("40")), BigDecimal.ZERO);
    }

    public static Freshness freshness() {
        return new Freshness(SPOT, FreshnessStatus.FRESH, START, START, INTERVAL,
                5L, 5L, new BigDecimal("500"), MarketSessionState.OPEN);
    }

    public static Freshness sessionClosedFreshness() {
        return new Freshness(SPOT, FreshnessStatus.FRESH, START, START, INTERVAL,
                5L, 5L, new BigDecimal("500"), MarketSessionState.CLOSED);
    }

    public static Freshness staleFreshness() {
        return new Freshness(SPOT, FreshnessStatus.STALE, START.minusSeconds(600),
                START.minusSeconds(600), INTERVAL, 600L, 600L,
                new BigDecimal("500"), MarketSessionState.OPEN);
    }

    public static Freshness agedFreshness() {
        return new Freshness(SPOT, FreshnessStatus.FRESH, START.minusSeconds(200),
                START.minusSeconds(200), INTERVAL, 200L, 200L,
                new BigDecimal("500"), MarketSessionState.OPEN);
    }

    public static TradeMonitoringContext context(ManualTrade trade,
                                                 Recommendation recommendation) {
        return context(trade, recommendation,
                RecommendationTestFixtures.bullishMarket(),
                RecommendationTestFixtures.bullishOptions(),
                RecommendationTestFixtures.qualification(),
                RecommendationTestFixtures.risk(), freshness(), Optional.empty());
    }

    public static TradeMonitoringContext context(ManualTrade trade,
                                                 Recommendation recommendation,
                                                 com.tradingassistant.intelligence.domain.MarketAssessment market,
                                                 OptionsAssessment options,
                                                 TradeQualification qualification,
                                                 RiskAssessment risk,
                                                 Freshness freshness,
                                                 Optional<TradeMonitoringResult> previous) {
        return new TradeMonitoringContext(trade, recommendation, market,
                RecommendationTestFixtures.indicators(), options,
                RecommendationTestFixtures.strategy(), qualification, risk, freshness,
                previous);
    }

    public static TradeMonitoringContext withPrevious(ManualTrade trade,
                                                      Recommendation recommendation,
                                                      TradeMonitoringResult previous) {
        return context(trade, recommendation, RecommendationTestFixtures.bullishMarket(),
                RecommendationTestFixtures.bullishOptions(),
                RecommendationTestFixtures.qualification(),
                RecommendationTestFixtures.risk(), freshness(), Optional.of(previous));
    }

    public static Map<ScorerId, BigDecimal> scores(String value) {
        Map<ScorerId, BigDecimal> values = new EnumMap<>(ScorerId.class);
        values.put(ScorerId.TREND, new BigDecimal(value));
        values.put(ScorerId.MOMENTUM, new BigDecimal(value));
        values.put(ScorerId.LIQUIDITY, new BigDecimal(value));
        return values;
    }
}
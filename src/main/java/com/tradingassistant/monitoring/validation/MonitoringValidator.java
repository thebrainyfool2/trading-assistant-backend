package com.tradingassistant.monitoring.validation;

import com.tradingassistant.intelligence.domain.AssessmentStatus;
import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.monitoring.config.MonitoringConfig;
import com.tradingassistant.monitoring.domain.ManualTrade;
import com.tradingassistant.monitoring.domain.TradeMonitoringContext;
import com.tradingassistant.monitoring.exception.MonitoringValidationException;
import com.tradingassistant.platform.readmodel.MarketDataFreshnessService.Freshness;
import com.tradingassistant.platform.readmodel.MarketDataFreshnessService.FreshnessStatus;
import com.tradingassistant.qualification.domain.TradeQualification;
import com.tradingassistant.recommendation.domain.Recommendation;
import com.tradingassistant.risk.domain.RiskAssessment;
import com.tradingassistant.strategy.domain.StrategyEvaluation;

import java.util.Objects;

/** Rejects closed, misaligned or stale inputs before deterministic rescoring. */
public final class MonitoringValidator {

    private final MonitoringConfig config;

    public MonitoringValidator(MonitoringConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    public TradeMonitoringContext align(TradeMonitoringContext context) {
        Objects.requireNonNull(context, "context must not be null");
        ManualTrade trade = context.trade();
        if (!trade.active()) {
            throw new MonitoringValidationException("closed trades are not monitored");
        }
        Recommendation recommendation = context.recommendation();
        InstrumentKey instrument = trade.instrument();
        Interval interval = trade.interval();
        if (!recommendation.instrument().equals(instrument)
                || recommendation.interval() != interval) {
            throw new MonitoringValidationException(
                    "recommendation does not match the trade instrument and interval");
        }
        if (!context.market().instrument().equals(instrument)
                || context.market().interval() != interval) {
            throw new MonitoringValidationException(
                    "market assessment does not match the trade instrument and interval");
        }
        if (context.market().status() == AssessmentStatus.INSUFFICIENT_DATA) {
            throw new MonitoringValidationException(
                    "market assessment has insufficient data");
        }
        if (!recommendation.timestamp().equals(context.market().timestamp())) {
            throw new MonitoringValidationException(
                    "recommendation timestamp does not match the market timestamp");
        }
        if (!context.options().spotInstrument().equals(instrument)) {
            throw new MonitoringValidationException(
                    "options assessment does not match the trade instrument");
        }
        if (!context.options().timestamp().equals(context.market().timestamp())) {
            throw new MonitoringValidationException(
                    "options assessment timestamp does not match the market timestamp");
        }
        alignStrategy(context.strategy(), instrument, interval, context.market().timestamp());
        alignQualification(context.qualification(), instrument, interval,
                context.market().timestamp());
        alignRisk(context.risk(), instrument, interval, context.market().timestamp());
        if (!context.qualification().strategyId().equals(context.strategy().strategyId())) {
            throw new MonitoringValidationException(
                    "strategy does not match the qualification strategy");
        }
        if (!context.qualification().strategyId().equals(context.risk().strategyId())) {
            throw new MonitoringValidationException(
                    "risk assessment does not match the qualification strategy");
        }
        Freshness freshness = context.freshness();
        if (!freshness.instrument().equals(instrument)) {
            throw new MonitoringValidationException(
                    "freshness does not match the trade instrument");
        }
        requireFresh(freshness);
        if (recommendation.option().isPresent()
                && !recommendation.option().get().expiry().equals(trade.expiry())) {
            throw new MonitoringValidationException(
                    "recommendation expiry does not match the trade expiry");
        }
        context.previousResult().ifPresent(previous -> {
            if (!previous.tradeId().equals(trade.tradeId())) {
                throw new MonitoringValidationException(
                        "previous result belongs to a different trade");
            }
            if (previous.timestamp().isAfter(context.market().timestamp())) {
                throw new MonitoringValidationException(
                        "previous result is newer than the current market timestamp");
            }
        });
        return context;
    }

    private void requireFresh(Freshness freshness) {
        if (freshness.status() != FreshnessStatus.FRESH) {
            throw new MonitoringValidationException(
                    "market data is not fresh: " + freshness.status());
        }
        long maximum = config.maximumStaleDuration().toSeconds();
        if (exceeds(freshness.tickAgeSeconds(), maximum)
                || exceeds(freshness.candleAgeSeconds(), maximum)) {
            throw new MonitoringValidationException(
                    "market data exceeds the configured maximum stale duration");
        }
    }

    private static boolean exceeds(Long ageSeconds, long maximumSeconds) {
        return ageSeconds != null && ageSeconds > maximumSeconds;
    }

    private static void alignStrategy(StrategyEvaluation strategy, InstrumentKey instrument,
                                      Interval interval, java.time.Instant timestamp) {
        if (!strategy.instrument().equals(instrument)
                || strategy.interval() != interval
                || !strategy.timestamp().equals(timestamp)) {
            throw new MonitoringValidationException(
                    "strategy evaluation is not aligned to the trade timestamp");
        }
    }

    private static void alignQualification(TradeQualification qualification,
                                           InstrumentKey instrument, Interval interval,
                                           java.time.Instant timestamp) {
        if (!qualification.instrument().equals(instrument)
                || qualification.interval() != interval
                || !qualification.timestamp().equals(timestamp)) {
            throw new MonitoringValidationException(
                    "qualification is not aligned to the trade timestamp");
        }
    }

    private static void alignRisk(RiskAssessment risk, InstrumentKey instrument,
                                  Interval interval, java.time.Instant timestamp) {
        if (!risk.instrument().equals(instrument)
                || risk.interval() != interval
                || !risk.timestamp().equals(timestamp)) {
            throw new MonitoringValidationException(
                    "risk assessment is not aligned to the trade timestamp");
        }
    }

}
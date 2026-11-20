package com.tradingassistant.platform.trade;

import com.tradingassistant.common.api.ErrorCode;
import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.monitoring.api.MonitoringDtos;
import com.tradingassistant.monitoring.domain.ManualTrade;
import com.tradingassistant.monitoring.domain.ManualTradeStatus;
import com.tradingassistant.monitoring.exception.MonitoringException;
import com.tradingassistant.monitoring.service.MonitoringQueryService;
import com.tradingassistant.monitoring.service.MonitoringService;
import com.tradingassistant.platform.error.PlatformException;
import com.tradingassistant.platform.instruments.InstrumentReferences;
import com.tradingassistant.recommendation.domain.Recommendation;
import com.tradingassistant.recommendation.domain.RecommendationContext;
import com.tradingassistant.recommendation.domain.RecommendationType;
import com.tradingassistant.recommendation.service.RecommendationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service for the owner's manual trade lifecycle. It records the
 * fill and delegates scoring to the existing monitoring service; it never
 * contacts a broker, places an order or changes a position.
 */
@Service
@ConditionalOnProperty(prefix = "app.broker.angel-one", name = "enabled",
        havingValue = "true")
public class ManualTradeCommandService {

    private final MonitoringService monitoring;
    private final MonitoringQueryService queries;
    private final RecommendationService recommendations;
    private final TradePlanService plans;
    private final TradePlanConfig config;
    private final Clock clock;

    public ManualTradeCommandService(MonitoringService monitoring,
                                     MonitoringQueryService queries,
                                     RecommendationService recommendations,
                                     TradePlanService plans,
                                     TradePlanConfig config,
                                     Clock clock) {
        this.monitoring = monitoring;
        this.queries = queries;
        this.recommendations = recommendations;
        this.plans = plans;
        this.config = config;
        this.clock = clock;
    }

    public MonitoringDtos.TradeView register(ManualTradeDtos.CreateTradeRequest request) {
        InstrumentKey instrument = InstrumentReferences.parse(request.instrument());
        RecommendationType type = parseType(request.recommendationType());
        Interval interval = parseInterval(request.interval());
        Instant pinned = requirePinnedTimestamp(request.recommendationTimestamp());
        Recommendation recommendation = recommendations.at(instrument, interval, pinned)
                .orElseThrow(() -> new PlatformException(ErrorCode.CONFLICT,
                        "The recommendation you acted on is no longer current. "
                                + "Refresh the recommendation, review the new plan and "
                                + "record the fill against it."));
        if (recommendation.type() != type) {
            throw new PlatformException(ErrorCode.VALIDATION_FAILED,
                    "recommendationType does not match the pinned recommendation ("
                            + recommendation.type() + ").");
        }
        Optional<RecommendationContext> context = recommendations.contextAt(
                instrument, interval, pinned);
        Optional<TradePlan> plan = context.flatMap(value ->
                plans.plan(recommendation, value));
        BigDecimal entry = request.entryPrice().setScale(2, RoundingMode.HALF_UP);
        BigDecimal target = request.targetPremium() == null
                ? plan.map(value -> entry.add(value.premiumTargetDistance()))
                .map(value -> value.setScale(2, RoundingMode.HALF_UP))
                .orElseGet(() -> percentageAbove(entry,
                        config.fallbackTargetPercent()))
                : request.targetPremium().setScale(2, RoundingMode.HALF_UP);
        BigDecimal stop = request.stopPremium() == null
                ? plan.map(value -> entry.subtract(value.premiumStopDistance()))
                .map(value -> value.setScale(2, RoundingMode.HALF_UP))
                .orElseGet(() -> percentageBelow(entry,
                        config.fallbackStopPercent()))
                : request.stopPremium().setScale(2, RoundingMode.HALF_UP);
        String tradeId = request.tradeId() == null || request.tradeId().isBlank()
                ? UUID.randomUUID().toString() : request.tradeId().trim();
        ManualTrade trade;
        try {
            trade = new ManualTrade(tradeId, instrument, interval, type,
                    request.optionSymbol().trim(), request.strike(), request.expiry(),
                    request.entryTimestamp() == null ? clock.instant()
                            : request.entryTimestamp(),
                    entry, request.quantity(), request.notes(),
                    ManualTradeStatus.ACTIVE, Optional.of(target), Optional.of(stop),
                    Optional.of(pinned));
        } catch (IllegalArgumentException failure) {
            throw new PlatformException(ErrorCode.VALIDATION_FAILED,
                    failure.getMessage());
        }
        try {
            monitoring.register(trade);
        } catch (MonitoringException failure) {
            throw translate(failure);
        }
        return MonitoringDtos.TradeView.from(queries.require(tradeId).trade());
    }

    public MonitoringDtos.TradeView close(String tradeId, String reason) {
        try {
            monitoring.close(tradeId, reason);
        } catch (MonitoringException failure) {
            throw translate(failure);
        }
        return MonitoringDtos.TradeView.from(queries.require(tradeId).trade());
    }

    private static BigDecimal percentageAbove(BigDecimal entry, BigDecimal percent) {
        return entry.add(entry.multiply(percent)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal percentageBelow(BigDecimal entry, BigDecimal percent) {
        return entry.subtract(entry.multiply(percent)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static Instant requirePinnedTimestamp(Instant value) {
        if (value == null) {
            throw new PlatformException(ErrorCode.VALIDATION_FAILED,
                    "recommendationTimestamp is required so the fill keeps the plan "
                            + "the owner acted on.");
        }
        return value;
    }

    private static RecommendationType parseType(String value) {
        try {
            RecommendationType type = RecommendationType.valueOf(
                    value.trim().toUpperCase(Locale.ROOT));
            if (type == RecommendationType.NO_TRADE) {
                throw new IllegalArgumentException("a manual trade must be BUY_CALL or BUY_PUT");
            }
            return type;
        } catch (IllegalArgumentException failure) {
            throw new PlatformException(ErrorCode.VALIDATION_FAILED,
                    "recommendationType must be BUY_CALL or BUY_PUT.");
        }
    }

    private static Interval parseInterval(String value) {
        if (value == null || value.isBlank()) {
            return Interval.FIVE_MINUTE;
        }
        try {
            return Interval.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            throw new PlatformException(ErrorCode.VALIDATION_FAILED,
                    "Unknown interval: " + value);
        }
    }

    private static PlatformException translate(MonitoringException failure) {
        ErrorCode code = failure.getMessage() != null
                && failure.getMessage().contains("already exists")
                ? ErrorCode.CONFLICT : ErrorCode.VALIDATION_FAILED;
        return new PlatformException(code, failure.getMessage());
    }
}

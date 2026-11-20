package com.tradingassistant.platform.trade;

import com.tradingassistant.market.domain.OptionType;
import com.tradingassistant.options.domain.ExpiryChain;
import com.tradingassistant.options.domain.OptionsAssessment;
import com.tradingassistant.options.domain.Strike;
import com.tradingassistant.recommendation.domain.OptionSelection;
import com.tradingassistant.recommendation.domain.Recommendation;
import com.tradingassistant.recommendation.domain.RecommendationContext;
import com.tradingassistant.recommendation.domain.RecommendationType;
import com.tradingassistant.risk.config.RiskEngineConfig;
import com.tradingassistant.risk.domain.RiskAssessment;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Converts already-computed recommendation evidence into one reference option
 * plan: entry, target, protective stop, a risk-sized quantity and the ATR/delta
 * basis behind them.
 *
 * <p>No indicator, option analytic or risk calculation is repeated. The service
 * only reads the selected chain leg, the risk assessment's ATR stop and the ATM
 * Greek. The output is a reference for the owner's manual execution.</p>
 */
@Service
@ConditionalOnProperty(prefix = "app.broker.angel-one", name = "enabled",
        havingValue = "true")
public class TradePlanService {

    private static final BigDecimal DEFAULT_TICK = new BigDecimal("0.05");

    private final RiskEngineConfig riskConfig;
    private final TradePlanConfig planConfig;
    private final Clock clock;

    public TradePlanService(RiskEngineConfig riskConfig, TradePlanConfig planConfig,
                            Clock clock) {
        this.riskConfig = Objects.requireNonNull(riskConfig, "riskConfig must not be null");
        this.planConfig = Objects.requireNonNull(planConfig, "planConfig must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public Optional<TradePlan> plan(Recommendation recommendation,
                                    RecommendationContext context) {
        Objects.requireNonNull(recommendation, "recommendation must not be null");
        Objects.requireNonNull(context, "context must not be null");
        if (recommendation.type() == RecommendationType.NO_TRADE
                || recommendation.option().isEmpty()) {
            return Optional.empty();
        }
        if (context.chain().isEmpty() || context.risks().isEmpty()) {
            return Optional.empty();
        }
        OptionSelection selection = recommendation.option().get();
        Strike leg = leg(context.chain().get().expiries(), selection);
        if (leg == null) {
            return Optional.empty();
        }
        BigDecimal last = leg.premium().orElse(null);
        BigDecimal entry = last != null ? last : leg.bestAsk().orElse(null);
        if (entry == null || entry.signum() <= 0) {
            return Optional.empty();
        }
        RiskAssessment risk = context.risks().stream()
                .filter(RiskAssessment::withinLimits)
                .findFirst()
                .orElse(context.risks().getFirst());
        BigDecimal underlyingStop = risk.stop().distance();
        if (underlyingStop == null || underlyingStop.signum() <= 0) {
            return Optional.empty();
        }
        BigDecimal underlyingTarget = underlyingStop.multiply(
                riskConfig.minimumRewardRiskRatio());
        Delta delta = delta(context.options(), selection.optionType());
        BigDecimal premiumStopDistance = underlyingStop.multiply(delta.value());
        BigDecimal premiumTargetDistance = underlyingTarget.multiply(delta.value());
        if (premiumStopDistance.signum() <= 0 || premiumTargetDistance.signum() <= 0) {
            return Optional.empty();
        }
        BigDecimal tick = tick(leg);
        if (entry.compareTo(tick) <= 0) {
            return Optional.empty();
        }
        BigDecimal stop = roundToTick(entry.subtract(premiumStopDistance), tick);
        BigDecimal target = roundToTick(entry.add(premiumTargetDistance), tick);
        if (stop.signum() <= 0) {
            stop = tick;
        }
        if (stop.compareTo(entry) >= 0) {
            stop = entry.subtract(tick);
        }
        if (target.compareTo(entry) <= 0) {
            target = entry.add(tick);
        }
        long lotSize = Math.max(1, leg.contract().instrument().lotSize());
        BigDecimal riskBudget = riskConfig.referenceCapital()
                .multiply(riskConfig.riskPercentFor(recommendation.instrument()))
                .divide(BigDecimal.valueOf(100), MathContext.DECIMAL128);
        BigDecimal riskPerUnit = entry.subtract(stop);
        BigDecimal riskPerLot = riskPerUnit.multiply(BigDecimal.valueOf(lotSize));
        BigDecimal lots = riskPerLot.signum() <= 0
                ? BigDecimal.ZERO
                : riskBudget.divide(riskPerLot, 0, RoundingMode.DOWN);
        BigDecimal quantity = lots.multiply(BigDecimal.valueOf(lotSize));
        BigDecimal maximumLoss = riskPerUnit.multiply(quantity);
        BigDecimal targetDistance = target.subtract(entry);
        BigDecimal potentialReward = targetDistance.multiply(quantity);
        BigDecimal rewardRisk = premiumStopDistance.signum() == 0
                ? BigDecimal.ZERO
                : targetDistance.divide(premiumStopDistance, 2, RoundingMode.HALF_UP);
        Map<String, String> diagnostics = new LinkedHashMap<>();
        diagnostics.put("underlyingStopDistance", plain(underlyingStop));
        diagnostics.put("underlyingTargetDistance", plain(underlyingTarget));
        diagnostics.put("riskRewardFloor", plain(riskConfig.minimumRewardRiskRatio()));
        diagnostics.put("deltaSource", delta.source());
        diagnostics.put("lotSize", Long.toString(lotSize));
        diagnostics.put("riskBudget", plain(riskBudget));
        diagnostics.put("riskPerLot", plain(riskPerLot));
        diagnostics.put("riskPercent", plain(riskConfig.riskPercentFor(
                recommendation.instrument())));
        if (last != null) {
            diagnostics.put("lastPremium", plain(last));
        }
        leg.bestAsk().ifPresent(value -> diagnostics.put("bestAsk", plain(value)));
        String basis = "ATR_STOP + " + delta.source() + " + REWARD_RISK";
        return Optional.of(new TradePlan(recommendation.type(),
                recommendation.instrument(), leg.contract().instrument().key(),
                leg.contract().instrument().tradingSymbol(), leg.expiry(),
                leg.strike(), selection.optionType(), last == null ? entry : last,
                entry, target, stop, targetDistance, entry.subtract(stop),
                underlyingTarget, underlyingStop, delta.value(), lotSize, quantity,
                maximumLoss, potentialReward, rewardRisk, basis,
                clock.instant(), Map.copyOf(diagnostics)));
    }

    private static Strike leg(java.util.List<ExpiryChain> expiries,
                              OptionSelection selection) {
        return expiries.stream()
                .filter(expiry -> expiry.expiry().equals(selection.expiry()))
                .findFirst()
                .flatMap(expiry -> expiry.leg(selection.strike(),
                        selection.optionType()))
                .orElse(null);
    }

    private Delta delta(Optional<OptionsAssessment> options, OptionType type) {
        if (options.isPresent()) {
            Optional<com.tradingassistant.options.domain.Greeks> greek =
                    type.isCall()
                            ? options.get().greeks().atmCall()
                            : options.get().greeks().atmPut();
            if (greek.isPresent() && greek.get().delta().signum() != 0) {
                return new Delta(greek.get().delta().abs(), "ATM_DELTA");
            }
        }
        return new Delta(planConfig.deltaFallback(), "FALLBACK_DELTA");
    }

    private static BigDecimal tick(Strike leg) {
        BigDecimal tick = leg.contract().instrument().tickSize();
        return tick == null || tick.signum() <= 0 ? DEFAULT_TICK : tick;
    }

    static BigDecimal roundToTick(BigDecimal value, BigDecimal tick) {
        return value.divide(tick, 0, RoundingMode.HALF_UP)
                .multiply(tick)
                .stripTrailingZeros();
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private record Delta(BigDecimal value, String source) {
    }
}
